package com.easycode.api.service;

import com.easycode.api.repo.RefreshTokenRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MaintenanceTasks {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceTasks.class);

    private final RefreshTokenRepository refreshTokens;

    public MaintenanceTasks(RefreshTokenRepository refreshTokens) {
        this.refreshTokens = refreshTokens;
    }

    /** Expired refresh tokens are dead weight — clear them out nightly. */
    @Scheduled(cron = "0 15 4 * * *")
    @Transactional
    public void purgeExpiredRefreshTokens() {
        int removed = refreshTokens.deleteExpired(Instant.now().minus(7, ChronoUnit.DAYS));
        if (removed > 0) {
            log.info("Purged {} expired refresh tokens", removed);
        }
    }
}
