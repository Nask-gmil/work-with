package jp.workwith.retention;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 保存期限切れデータを定期的に削除します。 */
@Component
@ConditionalOnProperty(
        name = "retention.cleanup.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class RetentionCleanupScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetentionCleanupScheduler.class);

    private final RetentionCleanupService cleanupService;

    public RetentionCleanupScheduler(RetentionCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @Scheduled(
            fixedDelayString = "${retention.cleanup-interval-milliseconds:3600000}",
            initialDelayString = "${retention.cleanup-initial-delay-milliseconds:60000}")
    public void removeExpiredData() {
        try {
            RetentionCleanupResult result = cleanupService.removeExpiredData(
                    LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
            if (result.deletedGlobalChatMessages() > 0) {
                LOGGER.info("CHAT_CLEANUP deletedCount={}", result.deletedGlobalChatMessages());
            }
            if (result.deletedDirectMessages() > 0) {
                LOGGER.info("DM_CLEANUP deletedCount={}", result.deletedDirectMessages());
            }
            if (!result.deletedPrivateRoomIds().isEmpty()) {
                LOGGER.info("PRIVATE_ROOM_CLEANUP deletedCount={}",
                        result.deletedPrivateRoomIds().size());
            }
        } catch (RuntimeException exception) {
            LOGGER.error("RETENTION_CLEANUP_FAILED operation=scheduled-cleanup errorType={}",
                    exception.getClass().getSimpleName());
        }
    }
}
