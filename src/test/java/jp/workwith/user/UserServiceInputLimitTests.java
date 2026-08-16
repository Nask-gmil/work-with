package jp.workwith.user;

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
    void rejectsOverlongPasswordBeforeBcryptAndDatabaseCreation() {
        UserRepository repository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        UserService service = new UserService(repository, passwordEncoder);
        when(repository.findByUsername("valid-user")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register("valid-user", "p".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("パスワードは64文字以内で入力してください");

        verify(passwordEncoder, never()).encode(any());
        verify(repository, never()).create(any());
    }
}
