package jp.workwith.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class UserRepositoryTests {

    @Autowired
    private UserRepository userRepository;

    /** INSERT、SELECT、UPDATE、DELETEを順番に実行して動作を確認します。 */
    @Test
    void performsUserCrud() {
        String username = "repository_test_" + UUID.randomUUID().toString().replace("-", "");
        User createdUser = null;

        try {
            // ① INSERT：テストユーザーを追加し、自動採番IDを受け取ります。
            createdUser = userRepository.create(new User(null, username, "test-password", "male_a"));
            assertThat(createdUser.getUserId()).isPositive();

            // ② SELECT：IDとusernameの両方で取得できることを確認します。
            Optional<User> foundById = userRepository.findById(createdUser.getUserId());
            Optional<User> foundByUsername = userRepository.findByUsername(username);
            assertThat(foundById).isPresent();
            assertThat(foundByUsername).isPresent();
            assertThat(foundByUsername.orElseThrow().getAvatarType()).isEqualTo("male_a");
            assertThat(userRepository.findAll())
                    .extracting(User::getUserId)
                    .contains(createdUser.getUserId());

            // ③ UPDATE：アバターを変更します。
            boolean updated = userRepository.updateAvatar(createdUser.getUserId(), "female_b");
            assertThat(updated).isTrue();

            // ④ SELECT：変更後の値を再取得して確認します。
            User updatedUser = userRepository.findById(createdUser.getUserId()).orElseThrow();
            assertThat(updatedUser.getAvatarType()).isEqualTo("female_b");
        } finally {
            // ⑤ DELETE：テストの成否にかかわらず、追加データを最後に削除します。
            if (createdUser != null && createdUser.getUserId() != null) {
                userRepository.deleteById(createdUser.getUserId());
            }
        }

        assertThat(userRepository.findByUsername(username)).isEmpty();
    }
}
