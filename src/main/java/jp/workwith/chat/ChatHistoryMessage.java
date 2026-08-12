package jp.workwith.chat;

import java.time.LocalDateTime;

public record ChatHistoryMessage(
        long messageId,
        long roomId,
        long userId,
        String username,
        Long targetUserId,
        String content,
        LocalDateTime sentAt) {
}
