package jp.workwith.retention;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

class RetentionCleanupSchedulerTests {

    @Test
    void delegatesPeriodicCleanupToService() {
        RetentionCleanupService service = mock(RetentionCleanupService.class);

        new RetentionCleanupScheduler(service).removeExpiredData();

        verify(service).removeExpiredData(any(LocalDateTime.class));
    }
}
