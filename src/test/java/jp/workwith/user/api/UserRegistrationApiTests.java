package jp.workwith.user.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import jp.workwith.user.User;
import jp.workwith.user.UserRepository;
import jp.workwith.registration.RegistrationRateLimitService;
import jp.workwith.registration.TurnstileService;
import jp.workwith.registration.TurnstileUnavailableException;

@SpringBootTest
@AutoConfigureMockMvc
class UserRegistrationApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RegistrationRateLimitService registrationRateLimitService;

    @MockitoBean
    private TurnstileService turnstileService;

    @BeforeEach
    void prepareRegistrationProtection() {
        registrationRateLimitService.clear();
        clearInvocations(turnstileService);
        when(turnstileService.verify(anyString(), anyString())).thenReturn(true);
    }

    @Test
    void registersUserWithHashedPasswordAndRejectsDuplicate() throws Exception {
        String username = "api_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
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

    @Test
    void acceptsBoundaryLengthsAndRejectsLongerValuesWithoutCreatingUsers() throws Exception {
        String username20 = "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 19);
        String username21 = username20 + "x";
        String password64 = "p".repeat(64);
        String password65 = "p".repeat(65);
        try {
            mockMvc.perform(post("/api/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registrationJson(username20, password64)))
                    .andExpect(status().isCreated());
            User saved = userRepository.findByUsername(username20).orElseThrow();
            assertThat(passwordEncoder.matches(password64, saved.getPassword())).isTrue();

            mockMvc.perform(post("/api/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registrationJson(username21, "password123")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("ユーザー名は20文字以内で入力してください"));
            assertThat(userRepository.findByUsername(username21)).isEmpty();

            String passwordUser = "p" + username20.substring(1);
            mockMvc.perform(post("/api/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registrationJson(passwordUser, password65)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("パスワードは64文字以内で入力してください"));
            assertThat(userRepository.findByUsername(passwordUser)).isEmpty();
        } finally {
            userRepository.findByUsername(username20)
                    .ifPresent(user -> userRepository.deleteById(user.getUserId()));
        }
    }

    @Test
    void registersLogsInAndDetectsDuplicatesUsingNormalizedJapaneseUsername() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        String decomposedUsername = "か\u3099く" + suffix;
        String normalizedUsername = "がく" + suffix;
        String password = "password123";
        try {
            mockMvc.perform(post("/api/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registrationJson(decomposedUsername, password)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.username").value(normalizedUsername));
            assertThat(userRepository.findByUsername(normalizedUsername)).isPresent();

            mockMvc.perform(post("/api/users/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"username":"%s","password":"%s"}
                            """.formatted(decomposedUsername, password)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.username").value(normalizedUsername));

            mockMvc.perform(post("/api/users/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registrationJson(normalizedUsername, password)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message")
                            .value("そのユーザー名はすでに使用されています"));
        } finally {
            userRepository.findByUsername(normalizedUsername)
                    .ifPresent(user -> userRepository.deleteById(user.getUserId()));
        }
    }

    @Test
    void rejectsMissingOrInvalidTurnstileWithoutCreatingUser() throws Exception {
        String username = "turnstile_reject_" + UUID.randomUUID().toString().replace("-", "");
        when(turnstileService.verify(anyString(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/users/register")
                .header("CF-Connecting-IP", "198.51.100.10")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson(username, "password123", "invalid-token")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("確認に失敗しました。もう一度お試しください。"));

        assertThat(userRepository.findByUsername(username)).isEmpty();
    }

    @Test
    void rejectsRegistrationWhenTurnstileCommunicationFails() throws Exception {
        String username = "turnstile_unavailable_" + UUID.randomUUID().toString().replace("-", "");
        when(turnstileService.verify(anyString(), anyString()))
                .thenThrow(new TurnstileUnavailableException());

        mockMvc.perform(post("/api/users/register")
                .header("CF-Connecting-IP", "198.51.100.11")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson(username, "password123", "token")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "登録処理を一時的に実行できません。時間を置いて再度お試しください。"));

        assertThat(userRepository.findByUsername(username)).isEmpty();
    }

    @Test
    void returns429BeforeCallingTurnstileAfterTenAttempts() throws Exception {
        String clientIp = "198.51.100.12";
        for (int attempt = 0; attempt < 10; attempt++) {
            mockMvc.perform(post("/api/users/register")
                    .header("CF-Connecting-IP", clientIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registrationJson("", "password123")))
                    .andExpect(status().isBadRequest());
        }
        verify(turnstileService, times(10)).verify(anyString(), anyString());
        clearInvocations(turnstileService);

        mockMvc.perform(post("/api/users/register")
                .header("CF-Connecting-IP", clientIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(registrationJson("", "password123")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(
                        "新規登録の試行回数が多すぎます。しばらくしてから再試行してください。"))
                .andExpect(result -> assertThat(result.getResponse().getHeader("Retry-After"))
                        .isNotBlank());

        verify(turnstileService, never()).verify(anyString(), anyString());
    }

    private String registrationJson(String username, String password) {
        return registrationJson(username, password, "valid-test-token");
    }

    private String registrationJson(String username, String password, String turnstileToken) {
        return """
                {
                  "username": "%s",
                  "password": "%s",
                  "turnstileToken": "%s"
                }
                """.formatted(username, password, turnstileToken);
    }
}
