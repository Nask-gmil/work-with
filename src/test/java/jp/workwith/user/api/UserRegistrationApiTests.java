package jp.workwith.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import jp.workwith.user.User;
import jp.workwith.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class UserRegistrationApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void registersUserWithHashedPasswordAndRejectsDuplicate() throws Exception {
        String username = "api_test_" + UUID.randomUUID().toString().replace("-", "");
        String plainPassword = "password123";

        try {
            // 1回目は登録成功し、レスポンスにパスワードを含めません。
            mockMvc.perform(post("/api/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registrationJson(username, plainPassword)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.userId").isNumber())
                    .andExpect(jsonPath("$.username").value(username))
                    .andExpect(jsonPath("$.password").doesNotExist());

            // DBには平文ではなく、BCryptで照合できるハッシュが保存されています。
            User savedUser = userRepository.findByUsername(username).orElseThrow();
            assertThat(savedUser.getPassword()).isNotEqualTo(plainPassword);
            assertThat(passwordEncoder.matches(plainPassword, savedUser.getPassword())).isTrue();
            assertThat(savedUser.getAvatarType()).isNull();

            // 同じusernameの2回目は409 Conflictで拒否します。
            mockMvc.perform(post("/api/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registrationJson(username, plainPassword)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("そのユーザー名はすでに使用されています"));
        } finally {
            userRepository.findByUsername(username)
                    .ifPresent(user -> userRepository.deleteById(user.getUserId()));
        }

        assertThat(userRepository.findByUsername(username)).isEmpty();
    }

    @Test
    void rejectsBlankUsernameAndPassword() throws Exception {
        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson("", "password123")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("ユーザー名を入力してください"));

        mockMvc.perform(post("/api/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson("new_user", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("パスワードを入力してください"));
    }

    private String registrationJson(String username, String password) {
        return """
                {
                  "username": "%s",
                  "password": "%s"
                }
                """.formatted(username, password);
    }
}
