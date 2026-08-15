package jp.workwith.retention;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 保存期限を過ぎたチャットとprivate部屋を、外部キー制約に沿って削除します。 */
@Repository
public class RetentionCleanupRepository {

    private final JdbcTemplate jdbcTemplate;

    public RetentionCleanupRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int deleteChatMessagesSentAtOrBefore(LocalDateTime cutoff) {
        return jdbcTemplate.update(
                "DELETE FROM CHAT_MESSAGES WHERE sent_at <= ?",
                Timestamp.valueOf(cutoff));
    }

    public List<Long> findExpiredPrivateRoomIds(LocalDateTime cutoff) {
        return jdbcTemplate.queryForList(
                """
                SELECT room_id
                FROM ROOMS
                WHERE room_type = 'private' AND created_at <= ?
                ORDER BY room_id
                """,
                Long.class,
                Timestamp.valueOf(cutoff));
    }

    public int deleteSeatAssignmentsByRoomId(long roomId) {
        return jdbcTemplate.update(
                """
                DELETE FROM SEAT_ASSIGNMENTS
                WHERE seat_id IN (SELECT seat_id FROM SEATS WHERE room_id = ?)
                """,
                roomId);
    }

    public int deleteChatMessagesByRoomId(long roomId) {
        return jdbcTemplate.update("DELETE FROM CHAT_MESSAGES WHERE room_id = ?", roomId);
    }

    public int deleteSeatsByRoomId(long roomId) {
        return jdbcTemplate.update("DELETE FROM SEATS WHERE room_id = ?", roomId);
    }

    public boolean deleteExpiredPrivateRoom(long roomId, LocalDateTime cutoff) {
        return jdbcTemplate.update(
                """
                DELETE FROM ROOMS
                WHERE room_id = ? AND room_type = 'private' AND created_at <= ?
                """,
                roomId,
                Timestamp.valueOf(cutoff)) == 1;
    }
}
