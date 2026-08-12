package jp.workwith.seatassignment.api;

import java.time.LocalDateTime;

import jp.workwith.seatassignment.RoomParticipant;

/** APIへ返す現在の座席割り当て情報です。 */
public record SeatAssignmentResponse(
        long seatId,
        long userId,
        String username,
        String avatarType,
        String status,
        String workContent,
        LocalDateTime startedAt,
        LocalDateTime lastHeartbeatAt) {

    public static SeatAssignmentResponse from(RoomParticipant participant) {
        return new SeatAssignmentResponse(
                participant.seatId(),
                participant.userId(),
                participant.username(),
                participant.avatarType(),
                participant.status(),
                participant.workContent(),
                participant.startedAt(),
                participant.lastHeartbeatAt());
    }
}
