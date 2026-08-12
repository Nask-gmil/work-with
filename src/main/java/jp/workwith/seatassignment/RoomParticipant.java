package jp.workwith.seatassignment;

import java.time.LocalDateTime;

/** 部屋画面で着席ユーザーを表示するために必要な情報です。 */
public record RoomParticipant(
        long seatId,
        long userId,
        String username,
        String avatarType,
        String status,
        String workContent,
        LocalDateTime startedAt,
        LocalDateTime lastHeartbeatAt) {
}
