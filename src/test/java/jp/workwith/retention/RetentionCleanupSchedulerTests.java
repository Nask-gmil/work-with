package jp.workwith.retention;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class RetentionCleanupSchedulerTests {

    @Test
    void delegatesPeriodicCleanupToService() {
        RetentionCleanupService service = mock(RetentionCleanupService.class);
        when(service.removeExpiredData(any(LocalDateTime.class)))
                .thenReturn(new RetentionCleanupResult(0, 0, Set.of()));

        new RetentionCleanupScheduler(service).removeExpiredData();

        verify(service).removeExpiredData(any(LocalDateTime.class));
    }

    @Test
    void writesOnlyAggregateSuccessLogsWhenSomethingWasDeleted() {
        RetentionCleanupService service = mock(RetentionCleanupService.class);
        when(service.removeExpiredData(any(LocalDateTime.class)))
                .thenReturn(new RetentionCleanupResult(24, 10, Set.of(1L, 2L)));
        ListAppender<ILoggingEvent> appender = appender();

        new RetentionCleanupScheduler(service).removeExpiredData();

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        "CHAT_CLEANUP deletedCount=24",
                        "DM_CLEANUP deletedCount=10",
                        "PRIVATE_ROOM_CLEANUP deletedCount=2");
    }

    @Test
    void logsFailureWithoutLeakingExceptionMessageOrStoppingFutureSchedules() {
        RetentionCleanupService service = mock(RetentionCleanupService.class);
        when(service.removeExpiredData(any(LocalDateTime.class)))
                .thenThrow(new IllegalStateException("secret-content"));
        ListAppender<ILoggingEvent> appender = appender();

        assertThatCode(() -> new RetentionCleanupScheduler(service).removeExpiredData())
                .doesNotThrowAnyException();

        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                .containsExactly(
                        "RETENTION_CLEANUP_FAILED operation=scheduled-cleanup errorType=IllegalStateException");
        assertThat(appender.list.getFirst().getFormattedMessage()).doesNotContain("secret-content");
    }

    private ListAppender<ILoggingEvent> appender() {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                LoggerFactory.getLogger(RetentionCleanupScheduler.class);
        logger.detachAndStopAllAppenders();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }
}
