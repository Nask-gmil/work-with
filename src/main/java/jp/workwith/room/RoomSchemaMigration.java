package jp.workwith.room;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;

/** 既存の開発用SQLiteへroom_codeを安全に追加する小さなマイグレーションです。 */
@Component
@Order(0)
public class RoomSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public RoomSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        List<String> columns = jdbcTemplate.query(
                "PRAGMA table_info(ROOMS)",
                (resultSet, rowNumber) -> resultSet.getString("name"));

        // SQLiteのADD COLUMNにはIF NOT EXISTSがないため、先に列一覧を確認します。
        if (!columns.contains("room_code")) {
            jdbcTemplate.execute("ALTER TABLE ROOMS ADD COLUMN room_code TEXT");
        }

        // 既存部屋を導入直後に期限切れ扱いしないよう、移行時刻で作成日時を補完します。
        if (!columns.contains("created_at")) {
            jdbcTemplate.execute("ALTER TABLE ROOMS ADD COLUMN created_at DATETIME");
            jdbcTemplate.execute("UPDATE ROOMS SET created_at = datetime('now', 'localtime')");
        } else {
            jdbcTemplate.execute("""
                    UPDATE ROOMS
                    SET created_at = datetime('now', 'localtime')
                    WHERE created_at IS NULL
                    """);
        }

        // 既存DBでも重複をDBレベルで防止します。NULLは複数行で使用できます。
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_rooms_room_code
                ON ROOMS (room_code)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS ix_rooms_private_created_at
                ON ROOMS (room_type, created_at)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS ix_chat_messages_sent_at
                ON CHAT_MESSAGES (sent_at)
                """);
    }
}
