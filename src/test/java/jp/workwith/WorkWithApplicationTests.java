package jp.workwith;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorkWithApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** SQLiteへ実際に問い合わせ、接続できることを確認します。 */
    @Test
    void connectsToSqlite() {
        String sqliteVersion = jdbcTemplate.queryForObject("SELECT sqlite_version()", String.class);

        assertThat(sqliteVersion).isNotBlank();
    }

    /** schema.sqlで必要な5テーブルが作成されたことを確認します。 */
    @Test
    void createsRequiredTables() {
        List<String> tableNames = jdbcTemplate.queryForList(
                "SELECT name FROM sqlite_master WHERE type = 'table'",
                String.class);

        assertThat(tableNames).contains(
                "USERS",
                "ROOMS",
                "SEATS",
                "SEAT_ASSIGNMENTS",
                "CHAT_MESSAGES");
    }

    /** SQLiteの外部キー制約が、この接続で有効になっていることを確認します。 */
    @Test
    void enablesForeignKeys() {
        Integer foreignKeysEnabled = jdbcTemplate.queryForObject("PRAGMA foreign_keys", Integer.class);

        assertThat(foreignKeysEnabled).isEqualTo(1);
    }

    /** 既存DBにもprivate部屋の保存期限判定用列が追加されることを確認します。 */
    @Test
    void roomsHaveCreatedAtForRetention() {
        List<String> roomColumns = jdbcTemplate.query(
                "PRAGMA table_info(ROOMS)",
                (resultSet, rowNumber) -> resultSet.getString("name"));

        assertThat(roomColumns).contains("created_at");
        Integer roomsWithoutCreatedAt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ROOMS WHERE created_at IS NULL", Integer.class);
        assertThat(roomsWithoutCreatedAt).isZero();
    }
}
