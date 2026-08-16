package jp.workwith.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jp.workwith.user.User;
import jp.workwith.user.UserRepository;
import jp.workwith.user.UserService;
import jp.workwith.user.UserSession;

@SpringBootTest
@AutoConfigureMockMvc
class UserSessionApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void createsSessionReturnsCurrentUserAndLogsOut() throws Exception {
        String username = "session_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String password = "session-test-password";
        User testUser = null;

        try {
            testUser = userService.register(username, password);

            // 古いセッションがあっても、ログイン成功時に新しいセッションへ交換します。
            MockHttpSession oldSession = new MockHttpSession();
            String oldSessionId = oldSession.getId();
            MvcResult loginResult = mockMvc.perform(post("/api/users/login")
                    .session(oldSession)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginJson(username, password)))
                    .andExpect(status().isOk())
                    .andReturn();

            MockHttpSession loginSession = (MockHttpSession) loginResult.getRequest().getSession(false);
            assertThat(loginSession).isNotNull();
            assertThat(loginSession.getId()).isNotEqualTo(oldSessionId);
            assertThat(loginSession.getAttribute(UserSession.LOGIN_USER_ID))
                    .isEqualTo(testUser.getUserId());

            // 同じセッションで/meを呼ぶと、パスワードを含まないユーザー情報を返します。
            mockMvc.perform(get("/api/users/me").session(loginSession))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(testUser.getUserId()))
                    .andExpect(jsonPath("$.username").value(username))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());

            // ログアウトで現在のセッションを無効化します。
            mockMvc.perform(post("/api/users/logout").session(loginSession))
                    .andExpect(status().isNoContent());
            assertThat(loginSession.isInvalid()).isTrue();
        } finally {
            if (testUser != null) {
                userRepository.deleteById(testUser.getUserId());
            }
        }

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsUnauthorizedForInvalidSessionStates() throws Exception {
        // セッションなし
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());

        // セッションはあるがLOGIN_USER_IDなし
        mockMvc.perform(get("/api/users/me").session(new MockHttpSession()))
                .andExpect(status().isUnauthorized());

        // DBに存在しないuserId
        MockHttpSession missingUserSession = new MockHttpSession();
        missingUserSession.setAttribute(UserSession.LOGIN_USER_ID, Long.MAX_VALUE);
        mockMvc.perform(get("/api/users/me").session(missingUserSession))
                .andExpect(status().isUnauthorized());
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
