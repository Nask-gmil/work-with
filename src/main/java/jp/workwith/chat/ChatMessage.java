package jp.workwith.chat;

import java.time.LocalDateTime;

public record ChatMessage(
        Long messageId,
        long roomId,
        long userId,
        Long targetUserId,
        String content,
        LocalDateTime sentAt) {
}
