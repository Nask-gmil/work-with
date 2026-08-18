package jp.workwith.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jp.workwith.user.DuplicateUsernameException;
import jp.workwith.user.UserService;

/**
 * 発表・評価用に固定のテストアカウントを起動時に用意します。
 * Renderの無料プランはデータが消えることがあるため、毎回の起動時に
 * 「なければ作る」形で復元します。
 *
 * 発表期間が終わったら、application.properties の test-account.enabled を
 * false にするか、このファイルごと削除してください。
 */
@Component
@ConditionalOnProperty(
        name = "test-account.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class TestAccountSeeder implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestAccountSeeder.class);

    // 発表用のテストアカウントです。変更したい場合はここを書き換えてください。
    private static final String TEST_USERNAME = "demo_user";
    private static final String TEST_PASSWORD = "Demo12345";

    private final UserService userService;

    public TestAccountSeeder(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            userService.register(TEST_USERNAME, TEST_PASSWORD);
            LOGGER.info("テストアカウント（{}）を作成しました", TEST_USERNAME);
        } catch (DuplicateUsernameException exception) {
            // 既に存在する場合は何もしません（再起動のたびに実行されるため）。
        }
    }
}
