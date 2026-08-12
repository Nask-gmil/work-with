package jp.workwith.seatassignment;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jp.workwith.realtime.RoomRealtimeNotifier;

@Component
@ConditionalOnProperty(
        name = "workwith.heartbeat-timeout.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class SeatAssignmentTimeoutScheduler {

    public static final Duration CHECK_INTERVAL = Duration.ofSeconds(30);

    private final SeatAssignmentTimeoutService timeoutService;
    private final RoomRealtimeNotifier realtimeNotifier;

    public SeatAssignmentTimeoutScheduler(
            SeatAssignmentTimeoutService timeoutService,
            RoomRealtimeNotifier realtimeNotifier) {
        this.timeoutService = timeoutService;
        this.realtimeNotifier = realtimeNotifier;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void removeExpiredAssignments() {
        Set<Long> changedRoomIds = timeoutService.removeExpiredAssignments(
                LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        changedRoomIds.forEach(realtimeNotifier::notifyParticipantsChanged);
    }
}
