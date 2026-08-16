package jp.workwith.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
        jdbcTemplate.update(
                "INSERT INTO ROOMS (room_id, room_type, created_at) VALUES (4, 'private', ?)",
                Timestamp.valueOf(now.minusDays(14)));
        for (long roomId = 1; roomId <= 4; roomId++) {
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
        jdbcTemplate.update(
                "INSERT INTO CHAT_MESSAGES (message_id, room_id, sent_at) VALUES (4, 2, ?)",
                Timestamp.valueOf(now.minusHours(23)));
        jdbcTemplate.update(
                "INSERT INTO CHAT_MESSAGES (message_id, room_id, target_user_id, sent_at) VALUES (5, 2, 1, ?)",
                Timestamp.valueOf(now.minusHours(24)));
        jdbcTemplate.update(
                "INSERT INTO CHAT_MESSAGES (message_id, room_id, target_user_id, sent_at) VALUES (6, 2, 1, ?)",
                Timestamp.valueOf(now.minusHours(23)));
        jdbcTemplate.update(
                "INSERT INTO CHAT_MESSAGES (message_id, room_id, target_user_id, sent_at) VALUES (7, 1, 1, ?)",
                Timestamp.valueOf(now.minusHours(1)));

        RetentionCleanupService service = new RetentionCleanupService(
                new RetentionCleanupRepository(jdbcTemplate), 24, 14);
        RetentionCleanupResult result = service.removeExpiredData(now);

        assertThat(result.deletedGlobalChatMessages()).isEqualTo(2);
        assertThat(result.deletedDirectMessages()).isEqualTo(2);
        assertThat(result.deletedPrivateRoomIds()).containsExactlyInAnyOrder(1L, 4L);
        assertThat(jdbcTemplate.queryForList("SELECT room_id FROM ROOMS ORDER BY room_id", Long.class))
                .containsExactly(2L, 3L);
        assertThat(jdbcTemplate.queryForList(
                "SELECT message_id FROM CHAT_MESSAGES ORDER BY message_id", Long.class))
                .containsExactly(3L, 4L, 6L);
        assertThat(jdbcTemplate.queryForList(
                "SELECT room_id FROM SEATS ORDER BY room_id", Long.class))
                .containsExactly(2L, 3L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SEAT_ASSIGNMENTS", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SEATS s LEFT JOIN ROOMS r ON r.room_id = s.room_id "
                        + "WHERE r.room_id IS NULL", Integer.class)).isZero();
    }

    @Test
    void rollsBackAllDeletesWhenPrivateRoomCleanupFailsMidway() {
        dataSource = new SingleConnectionDataSource(
                "jdbc:sqlite:" + temporaryDirectory.resolve("rollback.db"), true);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("PRAGMA foreign_keys=ON");
        createSchema(jdbcTemplate);
        LocalDateTime now = LocalDateTime.of(2026, 8, 16, 12, 0);
        jdbcTemplate.update("INSERT INTO USERS (user_id) VALUES (1)");
        jdbcTemplate.update(
                "INSERT INTO ROOMS (room_id, room_type, created_at) VALUES (1, 'private', ?)",
                Timestamp.valueOf(now.minusDays(15)));
        jdbcTemplate.update("INSERT INTO SEATS (seat_id, room_id) VALUES (1, 1)");
        jdbcTemplate.update("INSERT INTO SEAT_ASSIGNMENTS (seat_id, user_id) VALUES (1, 1)");
        jdbcTemplate.update(
                "INSERT INTO CHAT_MESSAGES (message_id, room_id, sent_at) VALUES (1, 1, ?)",
                Timestamp.valueOf(now.minusHours(1)));

        RetentionCleanupRepository failingRepository = new RetentionCleanupRepository(jdbcTemplate) {
            @Override
            public int deleteSeatsByRoomId(long roomId) {
                throw new IllegalStateException("simulated failure");
            }
        };
        RetentionCleanupService service = new RetentionCleanupService(failingRepository, 24, 14);
        TransactionTemplate transaction = new TransactionTemplate(
                new DataSourceTransactionManager(dataSource));

        assertThatThrownBy(() -> transaction.executeWithoutResult(
                ignored -> service.removeExpiredData(now)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ROOMS", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM SEATS", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM SEAT_ASSIGNMENTS", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM CHAT_MESSAGES", Integer.class)).isEqualTo(1);
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
                    target_user_id INTEGER REFERENCES USERS(user_id),
                    sent_at DATETIME NOT NULL
                )
                """);
    }
}
