package jp.workwith.chat;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ChatMessageRepository {

    private static final RowMapper<ChatMessage> ROW_MAPPER = (resultSet, rowNumber) -> {
        long targetUserId = resultSet.getLong("target_user_id");
        boolean targetUserIdWasNull = resultSet.wasNull();
        return new ChatMessage(
                resultSet.getLong("message_id"),
                resultSet.getLong("room_id"),
                resultSet.getLong("user_id"),
                targetUserIdWasNull ? null : targetUserId,
                resultSet.getString("content"),
                resultSet.getTimestamp("sent_at").toLocalDateTime());
    };

    private final JdbcTemplate jdbcTemplate;

    public ChatMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public ChatMessage create(ChatMessage message) {
        String sql = """
                INSERT INTO CHAT_MESSAGES
                    (room_id, user_id, target_user_id, content, sent_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, message.roomId());
            statement.setLong(2, message.userId());
            if (message.targetUserId() == null) {
                statement.setNull(3, java.sql.Types.BIGINT);
            } else {
                statement.setLong(3, message.targetUserId());
            }
            statement.setString(4, message.content());
            statement.setTimestamp(5, Timestamp.valueOf(message.sentAt()));
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("チャットメッセージIDを取得できませんでした");
        }
        return new ChatMessage(
                generatedId.longValue(), message.roomId(), message.userId(),
                message.targetUserId(), message.content(), message.sentAt());
    }

    public Optional<ChatMessage> findById(long messageId) {
        return jdbcTemplate.query(
                """
                SELECT message_id, room_id, user_id, target_user_id, content, sent_at
                FROM CHAT_MESSAGES
                WHERE message_id = ?
                """,
                ROW_MAPPER,
                messageId).stream().findFirst();
    }

    /** 指定部屋の全体チャット最新件を、画面表示用に古い順で返します。 */
    public List<ChatHistoryMessage> findLatestGlobalMessages(long roomId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT message_id, room_id, user_id, username, target_user_id, content, sent_at
                FROM (
                    SELECT cm.message_id, cm.room_id, cm.user_id, u.username,
                           cm.target_user_id, cm.content, cm.sent_at
                    FROM CHAT_MESSAGES cm
                    JOIN USERS u ON u.user_id = cm.user_id
                    WHERE cm.room_id = ?
                      AND cm.target_user_id IS NULL
                    ORDER BY cm.sent_at DESC, cm.message_id DESC
                    LIMIT ?
                ) latest_messages
                ORDER BY sent_at ASC, message_id ASC
                """,
                (resultSet, rowNumber) -> new ChatHistoryMessage(
                        resultSet.getLong("message_id"),
                        resultSet.getLong("room_id"),
                        resultSet.getLong("user_id"),
                        resultSet.getString("username"),
                        null,
                        resultSet.getString("content"),
                        resultSet.getTimestamp("sent_at").toLocalDateTime()),
                roomId,
                limit);
    }

    /** 同じ部屋にいる2人の個別チャット最新件を、古い順で返します。 */
    public List<ChatHistoryMessage> findLatestPrivateMessages(
            long roomId, long firstUserId, long secondUserId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT message_id, room_id, user_id, username,
                       target_user_id, content, sent_at
                FROM (
                    SELECT cm.message_id, cm.room_id, cm.user_id, u.username,
                           cm.target_user_id, cm.content, cm.sent_at
                    FROM CHAT_MESSAGES cm
                    JOIN USERS u ON u.user_id = cm.user_id
                    WHERE cm.room_id = ?
                      AND ((cm.user_id = ? AND cm.target_user_id = ?)
                        OR (cm.user_id = ? AND cm.target_user_id = ?))
                    ORDER BY cm.sent_at DESC, cm.message_id DESC
                    LIMIT ?
                ) latest_messages
                ORDER BY sent_at ASC, message_id ASC
                """,
                (resultSet, rowNumber) -> new ChatHistoryMessage(
                        resultSet.getLong("message_id"),
                        resultSet.getLong("room_id"),
                        resultSet.getLong("user_id"),
                        resultSet.getString("username"),
                        resultSet.getLong("target_user_id"),
                        resultSet.getString("content"),
                        resultSet.getTimestamp("sent_at").toLocalDateTime()),
                roomId,
                firstUserId,
                secondUserId,
                secondUserId,
                firstUserId,
                limit);
    }

    public List<ChatHistoryMessage> findLatestGlobalMessagesInPublicTheme(
            String theme, int limit) {
        return jdbcTemplate.query(
                """
                SELECT message_id, room_id, user_id, username, target_user_id, content, sent_at
                FROM (
                    SELECT cm.message_id, cm.room_id, cm.user_id, u.username,
                           cm.target_user_id, cm.content, cm.sent_at
                    FROM CHAT_MESSAGES cm
                    JOIN USERS u ON u.user_id = cm.user_id
                    JOIN ROOMS r ON r.room_id = cm.room_id
                    WHERE r.room_type = 'public'
                      AND r.theme = ?
                      AND cm.target_user_id IS NULL
                    ORDER BY cm.sent_at DESC, cm.message_id DESC
                    LIMIT ?
                ) latest_messages
                ORDER BY sent_at ASC, message_id ASC
                """,
                (resultSet, rowNumber) -> new ChatHistoryMessage(
                        resultSet.getLong("message_id"), resultSet.getLong("room_id"),
                        resultSet.getLong("user_id"), resultSet.getString("username"), null,
                        resultSet.getString("content"),
                        resultSet.getTimestamp("sent_at").toLocalDateTime()),
                theme, limit);
    }

    public List<ChatHistoryMessage> findLatestPrivateMessagesInPublicTheme(
            String theme, long firstUserId, long secondUserId, int limit) {
        return jdbcTemplate.query(
                """
                SELECT message_id, room_id, user_id, username,
                       target_user_id, content, sent_at
                FROM (
                    SELECT cm.message_id, cm.room_id, cm.user_id, u.username,
                           cm.target_user_id, cm.content, cm.sent_at
                    FROM CHAT_MESSAGES cm
                    JOIN USERS u ON u.user_id = cm.user_id
                    JOIN ROOMS r ON r.room_id = cm.room_id
                    WHERE r.room_type = 'public' AND r.theme = ?
                      AND ((cm.user_id = ? AND cm.target_user_id = ?)
                        OR (cm.user_id = ? AND cm.target_user_id = ?))
                    ORDER BY cm.sent_at DESC, cm.message_id DESC
                    LIMIT ?
                ) latest_messages
                ORDER BY sent_at ASC, message_id ASC
                """,
                (resultSet, rowNumber) -> new ChatHistoryMessage(
                        resultSet.getLong("message_id"), resultSet.getLong("room_id"),
                        resultSet.getLong("user_id"), resultSet.getString("username"),
                        resultSet.getLong("target_user_id"), resultSet.getString("content"),
                        resultSet.getTimestamp("sent_at").toLocalDateTime()),
                theme, firstUserId, secondUserId, secondUserId, firstUserId, limit);
    }

    public boolean deleteById(long messageId) {
        return jdbcTemplate.update(
                "DELETE FROM CHAT_MESSAGES WHERE message_id = ?", messageId) == 1;
    }
}
