package jp.workwith.user.api;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import jp.workwith.user.User;
import jp.workwith.user.LoginRateLimitService;
import jp.workwith.user.UserRepository;
import jp.workwith.user.UserService;

@SpringBootTest
@AutoConfigureMockMvc
class UserLoginApiTests {

    private static final String INVALID_CREDENTIALS_MESSAGE =
            "ユーザー名またはパスワードが正しくありません。";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginRateLimitService loginRateLimitService;

    @BeforeEach
    void clearLoginRateLimit() {
        loginRateLimitService.clear();
    }

    @Test
    void logsInWithCorrectPasswordAndRejectsInvalidCredentials() throws Exception {
        String username = "login_" + UUID.randomUUID().toString().replace("-", "").substring(0, 13);
        String correctPassword = "correct-password-123";
        User testUser = null;

        try {
            // 登録処理と同じServiceを使い、BCryptハッシュを持つテストユーザーを作ります。
            testUser = userService.register(username, correctPassword);

            // 正しい情報は200。レスポンスにパスワード関連の項目を含めません。
            mockMvc.perform(post("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson(username, correctPassword)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(testUser.getUserId()))
                    .andExpect(jsonPath("$.username").value(username))
                    .andExpect(jsonPath("$.avatarType").value(nullValue()))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());

            // 正しいusernameと誤ったpasswordは401です。
            mockMvc.perform(post("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson(username, "wrong-password")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS_MESSAGE));

            // 存在しないusernameも、同じステータスとメッセージにします。
            mockMvc.perform(post("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson("missing_" + username.substring(6, 18), correctPassword)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS_MESSAGE));
        } finally {
            if (testUser != null) {
                userRepository.deleteById(testUser.getUserId());
            }
        }
    }

    @Test
    void rejectsBlankLoginFields() throws Exception {
        mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("", "password123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ユーザー名を入力してください"));

        mockMvc.perform(post("/api/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson("testuser", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("パスワードを入力してください"));
    }

    @Test
    void tenthInvalidCredentialFailureStartsRateLimitAndBlocksCorrectPassword() throws Exception {
        String username = "limit_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String correctPassword = "correct-password-123";
        String clientIp = "192.0.2.80";
        User testUser = userService.register(username, correctPassword);
        try {
            for (int attempt = 1; attempt <= 9; attempt++) {
                mockMvc.perform(post("/api/users/login")
                        .header("CF-Connecting-IP", clientIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(username, "wrong-password")))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS_MESSAGE));
            }

            mockMvc.perform(post("/api/users/login")
                    .header("CF-Connecting-IP", clientIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson(username, "wrong-password")))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.message").value(
                            "ログイン試行回数が多いため、一時的にログインを制限しています。"
                                    + "時間を空けてから再度お試しください。"));

            mockMvc.perform(post("/api/users/login")
                    .header("CF-Connecting-IP", clientIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson(username, correctPassword)))
                    .andExpect(status().isTooManyRequests());
        } finally {
            userRepository.deleteById(testUser.getUserId());
            loginRateLimitService.clear();
        }
    }

    @Test
    void countsMissingUsernameFailuresWithoutRevealingWhetherTheUserExists() throws Exception {
        String username = "missing_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String clientIp = "192.0.2.81";

        for (int attempt = 1; attempt <= 9; attempt++) {
            mockMvc.perform(post("/api/users/login")
                    .header("CF-Connecting-IP", clientIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson(username, "wrong-password")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS_MESSAGE));
        }
        mockMvc.perform(post("/api/users/login")
                .header("CF-Connecting-IP", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(username, "wrong-password")))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void successfulJapaneseUsernameLoginResetsPreviousFailures() throws Exception {
        String username = "田中" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String correctPassword = "correct-password-123";
        String clientIp = "192.0.2.82";
        User testUser = userService.register(username, correctPassword);
        try {
            for (int attempt = 0; attempt < 3; attempt++) {
                performInvalidLogin(username, clientIp);
            }

            mockMvc.perform(post("/api/users/login")
                    .header("CF-Connecting-IP", clientIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson(username, correctPassword)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(username));

            // 成功前の3回が残っていれば7回目でロックされるため、9回すべて401ならリセット済みです。
            for (int attempt = 1; attempt <= 9; attempt++) {
                performInvalidLogin(username, clientIp);
            }
            mockMvc.perform(post("/api/users/login")
                    .header("CF-Connecting-IP", clientIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson(username, "wrong-password")))
                    .andExpect(status().isTooManyRequests());
        } finally {
            userRepository.deleteById(testUser.getUserId());
            loginRateLimitService.clear();
        }
    }

    private void performInvalidLogin(String username, String clientIp) throws Exception {
        mockMvc.perform(post("/api/users/login")
                .header("CF-Connecting-IP", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson(username, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(INVALID_CREDENTIALS_MESSAGE));
    }

    private String loginJson(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
    }
}
