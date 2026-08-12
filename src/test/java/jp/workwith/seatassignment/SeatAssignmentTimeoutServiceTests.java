package jp.workwith.seatassignment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class SeatAssignmentTimeoutServiceTests {

    private final SeatAssignmentRepository repository = mock(SeatAssignmentRepository.class);
    private final SeatAssignmentTimeoutService service =
            new SeatAssignmentTimeoutService(repository);

    @Test
    void removesAssignmentsAtOrBeyond180SecondsAndReturnsEachRoomOnce() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 12, 18, 35);
        LocalDateTime cutoff = now.minusSeconds(180);
        when(repository.findExpiredAssignments(cutoff)).thenReturn(List.of(
                new ExpiredSeatAssignment(11L, 5L, cutoff.minusSeconds(1)),
                new ExpiredSeatAssignment(12L, 5L, cutoff),
                new ExpiredSeatAssignment(21L, 8L, cutoff.minusMinutes(1))));
        when(repository.deleteIfHeartbeatExpired(11L, cutoff)).thenReturn(true);
        when(repository.deleteIfHeartbeatExpired(12L, cutoff)).thenReturn(true);
        when(repository.deleteIfHeartbeatExpired(21L, cutoff)).thenReturn(true);

        assertThat(service.removeExpiredAssignments(now)).containsExactly(5L, 8L);
    }

    @Test
    void doesNotReportAssignmentRefreshedBetweenSearchAndDelete() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 12, 18, 35);
        LocalDateTime cutoff = now.minusSeconds(180);
        when(repository.findExpiredAssignments(cutoff)).thenReturn(List.of(
                new ExpiredSeatAssignment(11L, 5L, cutoff)));
        when(repository.deleteIfHeartbeatExpired(11L, cutoff)).thenReturn(false);

        assertThat(service.removeExpiredAssignments(now)).isEmpty();
        verify(repository).deleteIfHeartbeatExpired(11L, cutoff);
    }
}
