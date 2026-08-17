package com.easycode.api.web;

import com.easycode.api.config.AppProperties;
import com.easycode.api.domain.Lead;
import com.easycode.api.domain.enums.LeadStatus;
import com.easycode.api.domain.enums.Role;
import com.easycode.api.repo.LeadRepository;
import com.easycode.api.repo.UserRepository;
import com.easycode.api.service.EmailService;
import com.easycode.api.web.dto.LeadDtos;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

/**
 * The marketing site's contact form. The old build swallowed its own errors and showed
 * "Message Sent!" either way — this endpoint validates, persists a real lead, and fails loudly.
 */
@RestController
@RequestMapping("/v1/public")
public class PublicController {

    private final LeadRepository leads;
    private final UserRepository users;
    private final EmailService email;
    private final AppProperties props;

    public PublicController(
            LeadRepository leads, UserRepository users, EmailService email, AppProperties props) {
        this.leads = leads;
        this.users = users;
        this.email = email;
        this.props = props;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("ok", true, "service", "easycode-api");
    }

    @PostMapping("/contact")
    public Map<String, Object> contact(@Valid @RequestBody LeadDtos.ContactFormInput body) {
        Lead lead = new Lead();
        lead.setBusinessName(
                body.business() == null || body.business().isBlank() ? body.name() : body.business());
        lead.setContactName(body.name());
        lead.setEmail(body.email().trim().toLowerCase());
        lead.setPhone(body.phone());
        lead.setSource("website");
        lead.setStatus(LeadStatus.NEW);
        lead.setNotes(body.message());
        Lead saved = leads.save(lead);

        String link = props.getBaseUrl() + "/admin/leads/" + saved.getId();
        users.findByRoleIn(List.of(Role.ADMIN)).forEach(admin -> email.send(
                admin.getEmail(),
                "New lead: " + saved.getBusinessName(),
                "<p><strong>" + saved.getContactName() + "</strong> (" + saved.getEmail() + ")</p>"
                        + "<p>" + saved.getNotes() + "</p>"
                        + "<p><a href=\"" + link + "\">Open in the pipeline</a></p>"));

        return Map.of("ok", true, "leadId", saved.getId());
    }
}
