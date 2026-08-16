package jp.workwith.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceInputLimitTests {

    @Test
    void acceptsExistingAndJapaneseUsernameForms() {
        UserRepository repository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserService service = new UserService(repository, passwordEncoder);
        when(repository.findByUsername(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(repository.create(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return new User(1L, user.getUsername(), user.getPassword(), null);
        });

        for (String username : new String[] {
                "user01", "user_01", "たなか", "タナカ", "田中", "田中01", "田中_01"
        }) {
            assertThat(service.register(username, "password123").getUsername())
                    .isEqualTo(username);
        }
        assertThat(service.register("田".repeat(20), "password123").getUsername())
                .hasSize(20);
    }

    @Test
    void rejectsSpacesEmojiSymbolsAndMoreThanTwentyCharacters() {
        UserService service = new UserService(
                mock(UserRepository.class), mock(PasswordEncoder.class));

        for (String username : new String[] {
                "田中 太郎", "田中　太郎", "田中😊", "田中!", "user@", "user-name"
        }) {
            assertThatThrownBy(() -> service.register(username, "password123"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("半角英数字");
        }
        assertThatThrownBy(() -> service.register("田".repeat(21), "password123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ユーザー名は20文字以内で入力してください");
    }

    @Test
    void normalizesUsernameToNfcForRegistrationAndLoginWithoutChangingAsciiCase() {
        UserRepository repository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserService service = new UserService(repository, passwordEncoder);
        String decomposed = "か\u3099く";
        String normalized = "がく";
        when(repository.findByUsername(normalized)).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(repository.create(any())).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return new User(1L, user.getUsername(), user.getPassword(), null);
        });

        assertThat(service.register(decomposed, "password123").getUsername())
                .isEqualTo(normalized);

        when(repository.findByUsername(normalized))
                .thenReturn(Optional.of(new User(1L, normalized, "hash", null)));
        when(passwordEncoder.matches("password123", "hash")).thenReturn(true);
        assertThat(service.login(decomposed, "password123").getUsername())
                .isEqualTo(normalized);
        assertThat(service.register("User01", "password123").getUsername())
                .isEqualTo("User01");
        verify(repository).findByUsername("User01");
    }

    @Test
    void rejectsOverlongPasswordBeforeBcryptAndDatabaseCreation() {
        UserRepository repository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserService service = new UserService(repository, passwordEncoder);
        when(repository.findByUsername("valid_user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register("valid_user", "p".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("パスワードは64文字以内で入力してください");

        verify(passwordEncoder, never()).encode(any());
        verify(repository, never()).create(any());
    }
}
