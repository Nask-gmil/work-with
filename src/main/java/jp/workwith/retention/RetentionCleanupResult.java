package jp.workwith.retention;

import java.util.Set;

/** 1回の保存期限削除で変更された件数とprivate部屋IDです。 */
public record RetentionCleanupResult(
        int deletedGlobalChatMessages,
        int deletedDirectMessages,
        Set<Long> deletedPrivateRoomIds) {

    public int deletedChatMessages() {
        return deletedGlobalChatMessages + deletedDirectMessages;
    }
}
