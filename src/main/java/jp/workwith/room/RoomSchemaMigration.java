package jp.workwith.room;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 既存の開発用SQLiteへroom_codeを安全に追加する小さなマイグレーションです。 */
@Component
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

        // 既存DBでも重複をDBレベルで防止します。NULLは複数行で使用できます。
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS ux_rooms_room_code
                ON ROOMS (room_code)
                """);
    }
}
