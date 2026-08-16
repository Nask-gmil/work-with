package jp.workwith.seatassignment.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;

import jp.workwith.seatassignment.RoomParticipant;

/** APIへ返す現在の座席割り当て情報です。 */
public record SeatAssignmentResponse(
        long seatId,
        long userId,
        String username,
        String avatarType,
        String status,
        String workContent,
        OffsetDateTime startedAt,
        OffsetDateTime lastHeartbeatAt) {

    public static SeatAssignmentResponse from(RoomParticipant participant) {
        return new SeatAssignmentResponse(
                participant.seatId(),
                participant.userId(),
                participant.username(),
                participant.avatarType(),
                participant.status(),
                participant.workContent(),
                withServerOffset(participant.startedAt()),
                withServerOffset(participant.lastHeartbeatAt()));
    }

    /** DBのタイムゾーンなし日時に、保存時と同じサーバーのUTCオフセットを付けます。 */
    private static OffsetDateTime withServerOffset(java.time.LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
