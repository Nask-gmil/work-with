package jp.workwith.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import jp.workwith.user.User;
import jp.workwith.user.UserRepository;
import jp.workwith.user.UserService;
import jp.workwith.user.UserSession;

@SpringBootTest
@AutoConfigureMockMvc
class UserAvatarApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void updatesAvatarAndReturnsItFromCurrentUserApi() throws Exception {
        User user = createTestUser();
        MockHttpSession session = loggedInSession(user);

        try {
            mockMvc.perform(patch("/api/users/me/avatar")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"avatarType\":\"male_a\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userId").value(user.getUserId()))
                    .andExpect(jsonPath("$.username").value(user.getUsername()))
                    .andExpect(jsonPath("$.avatarType").value("male_a"))
                    .andExpect(jsonPath("$.password").doesNotExist())
                    .andExpect(jsonPath("$.passwordHash").doesNotExist());

            mockMvc.perform(get("/api/users/me").session(session))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avatarType").value("male_a"));

            // 初回選択だけでなく、将来の変更にも同じAPIを再利用できます。
            mockMvc.perform(patch("/api/users/me/avatar")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"avatarType\":\"female_b\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avatarType").value("female_b"));

            assertThat(userRepository.findById(user.getUserId()))
                    .get()
                    .extracting(User::getAvatarType)
                    .isEqualTo("female_b");
        } finally {
            userRepository.deleteById(user.getUserId());
        }
    }

    @Test
    void rejectsInvalidAvatarWithoutChangingDatabase() throws Exception {
        User user = createTestUser();
        MockHttpSession session = loggedInSession(user);

        try {
            mockMvc.perform(patch("/api/users/me/avatar")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"avatarType\":\"invalid_avatar\"}"))
                    .andExpect(status().isBadRequest());

            mockMvc.perform(patch("/api/users/me/avatar")
                    .session(session)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"avatarType\":\"\"}"))
                    .andExpect(status().isBadRequest());

            assertThat(userRepository.findById(user.getUserId()))
                    .get()
                    .extracting(User::getAvatarType)
                    .isNull();
        } finally {
            userRepository.deleteById(user.getUserId());
        }
    }

    @Test
    void rejectsUnauthenticatedAndMissingUsers() throws Exception {
        mockMvc.perform(patch("/api/users/me/avatar")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"avatarType\":\"male_a\"}"))
                .andExpect(status().isUnauthorized());

        MockHttpSession missingUserSession = new MockHttpSession();
        missingUserSession.setAttribute(UserSession.LOGIN_USER_ID, Long.MAX_VALUE);
        mockMvc.perform(patch("/api/users/me/avatar")
                .session(missingUserSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"avatarType\":\"male_a\"}"))
                .andExpect(status().isUnauthorized());
    }

    private User createTestUser() {
        String username = "avatar_test_" + UUID.randomUUID().toString().replace("-", "");
        return userService.register(username, "avatar-test-password");
    }

    private MockHttpSession loggedInSession(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(UserSession.LOGIN_USER_ID, user.getUserId());
        return session;
    }
}
