package jp.workwith.retention;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** チャット24時間・private部屋14日という保存期間を適用します。 */
@Service
public class RetentionCleanupService {

    private final RetentionCleanupRepository repository;
    private final long chatRetentionHours;
    private final long privateRoomRetentionDays;

    public RetentionCleanupService(
            RetentionCleanupRepository repository,
            @Value("${retention.chat-hours:24}") long chatRetentionHours,
            @Value("${retention.private-room-days:14}") long privateRoomRetentionDays) {
        if (chatRetentionHours < 1 || privateRoomRetentionDays < 1) {
            throw new IllegalArgumentException("保存期間は1以上にしてください");
        }
        this.repository = repository;
        this.chatRetentionHours = chatRetentionHours;
        this.privateRoomRetentionDays = privateRoomRetentionDays;
    }

    @Transactional
    public RetentionCleanupResult removeExpiredData(LocalDateTime now) {
        int deletedChats = repository.deleteChatMessagesSentAtOrBefore(
                now.minusHours(chatRetentionHours));
        LocalDateTime roomCutoff = now.minusDays(privateRoomRetentionDays);
        Set<Long> deletedRoomIds = new LinkedHashSet<>();

        for (long roomId : repository.findExpiredPrivateRoomIds(roomCutoff)) {
            repository.deleteSeatAssignmentsByRoomId(roomId);
            deletedChats += repository.deleteChatMessagesByRoomId(roomId);
            repository.deleteSeatsByRoomId(roomId);
            if (repository.deleteExpiredPrivateRoom(roomId, roomCutoff)) {
                deletedRoomIds.add(roomId);
            }
        }
        return new RetentionCleanupResult(deletedChats, Set.copyOf(deletedRoomIds));
    }
}
