package jp.workwith.seatassignment;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeatAssignmentTimeoutService {

    public static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(180);

    private final SeatAssignmentRepository seatAssignmentRepository;

    public SeatAssignmentTimeoutService(SeatAssignmentRepository seatAssignmentRepository) {
        this.seatAssignmentRepository = seatAssignmentRepository;
    }

    /** 期限切れの割り当てだけを削除し、実際に変更された部屋IDを返します。 */
    @Transactional
    public Set<Long> removeExpiredAssignments(LocalDateTime now) {
        LocalDateTime cutoffTime = now.minus(HEARTBEAT_TIMEOUT);
        Set<Long> changedRoomIds = new LinkedHashSet<>();

        for (ExpiredSeatAssignment assignment
                : seatAssignmentRepository.findExpiredAssignments(cutoffTime)) {
            // 検索後にheartbeatが届いた場合は削除しないよう、DELETEでも期限を再確認します。
            if (seatAssignmentRepository.deleteIfHeartbeatExpired(
                    assignment.seatId(), cutoffTime)) {
                changedRoomIds.add(assignment.roomId());
            }
        }
        return changedRoomIds;
    }
}
