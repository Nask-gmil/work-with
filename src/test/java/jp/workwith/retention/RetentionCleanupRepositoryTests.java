package jp.workwith.retention;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class RetentionCleanupRepositoryTests {

    @TempDir
    Path temporaryDirectory;

    private SingleConnectionDataSource dataSource;

    @AfterEach
    void closeDatabaseConnection() {
        if (dataSource != null) dataSource.destroy();
    }

    @Test
    void deletesExpiredDataFromRealSqliteWithoutDeletingPublicOrCurrentData() {
        dataSource = new SingleConnectionDataSource(
                "jdbc:sqlite:" + temporaryDirectory.resolve("retention.db"), true);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("PRAGMA foreign_keys=ON");
        createSchema(jdbcTemplate);

        LocalDateTime now = LocalDateTime.of(2026, 8, 16, 12, 0);
        jdbcTemplate.update("INSERT INTO USERS (user_id) VALUES (1)");
        jdbcTemplate.update(
                "INSERT INTO ROOMS (room_id, room_type, created_at) VALUES (1, 'private', ?)",
                Timestamp.valueOf(now.minusDays(15)));
        jdbcTemplate.update(
                "INSERT INTO ROOMS (room_id, room_type, created_at) VALUES (2, 'private', ?)",
                Timestamp.valueOf(now.minusDays(13)));
        jdbcTemplate.update(
                "INSERT INTO ROOMS (room_id, room_type, created_at) VALUES (3, 'public', ?)",
                Timestamp.valueOf(now.minusDays(30)));
        for (long roomId = 1; roomId <= 3; roomId++) {
            jdbcTemplate.update("INSERT INTO SEATS (seat_id, room_id) VALUES (?, ?)",
                    roomId, roomId);
        }
        jdbcTemplate.update("INSERT INTO SEAT_ASSIGNMENTS (seat_id, user_id) VALUES (1, 1)");
        jdbcTemplate.update(
                "INSERT INTO CHAT_MESSAGES (message_id, room_id, sent_at) VALUES (1, 1, ?)",
                Timestamp.valueOf(now.minusHours(1)));
        jdbcTemplate.update(
                "INSERT INTO CHAT_MESSAGES (message_id, room_id, sent_at) VALUES (2, 2, ?)",
                Timestamp.valueOf(now.minusHours(25)));
        jdbcTemplate.update(
                "INSERT INTO CHAT_MESSAGES (message_id, room_id, sent_at) VALUES (3, 3, ?)",
                Timestamp.valueOf(now.minusHours(1)));

        RetentionCleanupService service = new RetentionCleanupService(
                new RetentionCleanupRepository(jdbcTemplate), 24, 14);
        RetentionCleanupResult result = service.removeExpiredData(now);

        assertThat(result.deletedChatMessages()).isEqualTo(2);
        assertThat(result.deletedPrivateRoomIds()).containsExactly(1L);
        assertThat(jdbcTemplate.queryForList("SELECT room_id FROM ROOMS ORDER BY room_id", Long.class))
                .containsExactly(2L, 3L);
        assertThat(jdbcTemplate.queryForList(
                "SELECT message_id FROM CHAT_MESSAGES ORDER BY message_id", Long.class))
                .containsExactly(3L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SEAT_ASSIGNMENTS", Integer.class)).isZero();
    }

    private void createSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("CREATE TABLE USERS (user_id INTEGER PRIMARY KEY)");
        jdbcTemplate.execute("""
                CREATE TABLE ROOMS (
                    room_id INTEGER PRIMARY KEY,
                    room_type TEXT NOT NULL,
                    created_at DATETIME NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE SEATS (
                    seat_id INTEGER PRIMARY KEY,
                    room_id INTEGER NOT NULL REFERENCES ROOMS(room_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE SEAT_ASSIGNMENTS (
                    seat_id INTEGER PRIMARY KEY REFERENCES SEATS(seat_id),
                    user_id INTEGER NOT NULL REFERENCES USERS(user_id)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE CHAT_MESSAGES (
                    message_id INTEGER PRIMARY KEY,
                    room_id INTEGER NOT NULL REFERENCES ROOMS(room_id),
                    sent_at DATETIME NOT NULL
                )
                """);
    }
}
