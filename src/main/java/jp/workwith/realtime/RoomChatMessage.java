package jp.workwith.realtime;

import java.time.LocalDateTime;

public record RoomChatMessage(
        String type, long messageId, long roomId, long userId, String username,
        Long targetUserId,
        String content, LocalDateTime sentAt) {
}
