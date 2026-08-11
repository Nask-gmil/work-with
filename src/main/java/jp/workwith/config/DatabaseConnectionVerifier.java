package jp.workwith.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * アプリ起動時にSQLiteへ接続できることを確認します。
 * 後からテーブルを作成しても、このクラスは接続確認用としてそのまま利用できます。
 */
@Component
public class DatabaseConnectionVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnectionVerifier.class);

    private final JdbcTemplate jdbcTemplate;

    public DatabaseConnectionVerifier(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        String sqliteVersion = jdbcTemplate.queryForObject("SELECT sqlite_version()", String.class);
        log.info("SQLiteへの接続に成功しました。SQLite version: {}", sqliteVersion);
    }
}
