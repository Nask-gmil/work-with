package jp.workwith.user.api;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import jp.workwith.user.User;
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

    private String loginJson(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
    }
}
