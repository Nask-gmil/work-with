package jp.workwith.retention;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 保存期限切れデータを定期的に削除します。 */
@Component
@ConditionalOnProperty(
        name = "retention.cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RetentionCleanupScheduler {

    private final RetentionCleanupService cleanupService;

    public RetentionCleanupScheduler(RetentionCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(
            fixedDelayString = "${retention.cleanup-interval-milliseconds:3600000}",
            initialDelayString = "${retention.cleanup-initial-delay-milliseconds:60000}")
    public void removeExpiredData() {
        cleanupService.removeExpiredData(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
    }
}
