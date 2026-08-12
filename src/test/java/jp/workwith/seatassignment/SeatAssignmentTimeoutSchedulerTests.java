package jp.workwith.seatassignment;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.Test;

import jp.workwith.realtime.RoomRealtimeNotifier;

class SeatAssignmentTimeoutSchedulerTests {

    @Test
    void notifiesOnlyRoomsWhoseAssignmentsWereDeleted() {
        SeatAssignmentTimeoutService service = mock(SeatAssignmentTimeoutService.class);
        RoomRealtimeNotifier notifier = mock(RoomRealtimeNotifier.class);
        when(service.removeExpiredAssignments(any(LocalDateTime.class)))
                .thenReturn(Set.of(5L, 8L));

        new SeatAssignmentTimeoutScheduler(service, notifier).removeExpiredAssignments();

        verify(notifier).notifyParticipantsChanged(5L);
        verify(notifier).notifyParticipantsChanged(8L);
    }

    @Test
    void sendsNoNotificationWhenNothingExpired() {
        SeatAssignmentTimeoutService service = mock(SeatAssignmentTimeoutService.class);
        RoomRealtimeNotifier notifier = mock(RoomRealtimeNotifier.class);
        when(service.removeExpiredAssignments(any(LocalDateTime.class))).thenReturn(Set.of());

        new SeatAssignmentTimeoutScheduler(service, notifier).removeExpiredAssignments();

        verify(notifier, never()).notifyParticipantsChanged(any(Long.class));
    }
}
