package com.easycode.api.service;

import com.easycode.api.domain.ChecklistTemplate;
import com.easycode.api.domain.ChecklistTemplateItem;
import com.easycode.api.repo.ChecklistTemplateItemRepository;
import com.easycode.api.repo.ChecklistTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads checklist templates from {@code checklist-seed.json} on boot.
 *
 * <p>The markdown checklist documents are the source of truth; {@code build-seed.py}
 * turns them into that JSON. This service compares each template's content hash
 * against the newest stored version and writes a NEW version when it differs. It
 * never edits an existing version, because projects reference the version they were
 * created against.
 *
 * <p>Doing nothing is the normal outcome. On an unchanged deploy this logs one line
 * and returns.
 */
@Service
public class ChecklistSeedService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChecklistSeedService.class);
    private static final String SEED = "checklist-seed.json";

    private final ChecklistTemplateRepository templates;
    private final ChecklistTemplateItemRepository items;
    private final ObjectMapper mapper;

    public ChecklistSeedService(
            ChecklistTemplateRepository templates,
            ChecklistTemplateItemRepository items,
            ObjectMapper mapper) {
        this.templates = templates;
        this.items = items;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ClassPathResource resource = new ClassPathResource(SEED);
        if (!resource.exists()) {
            log.warn("{} not on the classpath — no checklist templates seeded", SEED);
            return;
        }

        JsonNode root;
        try (InputStream in = resource.getInputStream()) {
            root = mapper.readTree(in);
        } catch (Exception e) {
            // A malformed seed must not stop the application from starting.
            log.error("Could not read {}; skipping checklist seeding", SEED, e);
            return;
        }

        int added = 0;
        for (JsonNode t : root.path("templates")) {
            String type = t.path("projectType").asText();
            String hash = t.path("contentHash").asText();
            if (type.isBlank() || hash.isBlank()) {
                continue;
            }

            Optional<ChecklistTemplate> newest =
                    templates.findFirstByProjectTypeOrderByVersionDesc(type);
            if (newest.isPresent() && hash.equals(newest.get().getContentHash())) {
                continue; // unchanged
            }

            ChecklistTemplate tpl = new ChecklistTemplate();
            tpl.setProjectType(type);
            tpl.setLabel(t.path("label").asText(type));
            tpl.setVersion(newest.map(c -> c.getVersion() + 1).orElse(1));
            tpl.setContentHash(hash);
            ChecklistTemplate saved = templates.save(tpl);

            List<ChecklistTemplateItem> batch = new ArrayList<>();
            for (JsonNode i : t.path("items")) {
                ChecklistTemplateItem item = new ChecklistTemplateItem();
                item.setTemplateId(saved.getId());
                item.setStageKey(i.path("stage").asText());
                item.setPosition(i.path("position").asInt());
                item.setTitle(i.path("title").asText());
                item.setGuidance(i.path("guidance").asText(null));
                item.setEmphasis(i.path("emphasis").asBoolean(false));
                batch.add(item);
            }
            items.saveAll(batch);
            added++;
            log.info("Seeded checklist {} v{} ({} items)", type, saved.getVersion(), batch.size());
        }

        if (added == 0) {
            log.info("Checklist templates already current");
        }
    }
}