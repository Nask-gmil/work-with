package jp.workwith.seatassignment.api;

import java.time.LocalDateTime;

import jp.workwith.seatassignment.SeatAssignment;

/** APIへ返す現在の座席割り当て情報です。 */
public record SeatAssignmentResponse(
        long seatId,
        long userId,
        String status,
        String workContent,
        LocalDateTime startedAt,
        LocalDateTime lastHeartbeatAt) {

    public static SeatAssignmentResponse from(SeatAssignment assignment) {
        return new SeatAssignmentResponse(
                assignment.getSeatId(),
                assignment.getUserId(),
                assignment.getStatus(),
                assignment.getWorkContent(),
                assignment.getStartedAt(),
                assignment.getLastHeartbeatAt());
    }
}
