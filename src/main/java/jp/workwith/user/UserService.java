package jp.workwith.user;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** ユーザー登録に必要な確認とパスワードのハッシュ化を担当します。 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String username, String password) {
        String normalizedUsername = username == null ? "" : username.trim();

        if (normalizedUsername.isEmpty()) {
            throw new IllegalArgumentException("ユーザー名を入力してください");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("パスワードを入力してください");
        }
        if (userRepository.findByUsername(normalizedUsername).isPresent()) {
            throw new DuplicateUsernameException();
        }

        // 平文パスワードは保存せず、BCryptハッシュだけをRepositoryへ渡します。
        String passwordHash = passwordEncoder.encode(password);
        User newUser = new User(null, normalizedUsername, passwordHash, null);

        try {
            return userRepository.create(newUser);
        } catch (DuplicateKeyException exception) {
            // 同時リクエストによる重複もDBのUNIQUE制約と合わせて安全に扱います。
            throw new DuplicateUsernameException();
        }
    }

    /** usernameと平文パスワードを照合し、成功したユーザーを返します。 */
    public User login(String username, String password) {
        String normalizedUsername = username == null ? "" : username.trim();

        if (normalizedUsername.isEmpty()) {
            throw new IllegalArgumentException("ユーザー名を入力してください");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("パスワードを入力してください");
        }

        User user = userRepository.findByUsername(normalizedUsername)
                .orElseThrow(InvalidCredentialsException::new);

        // BCryptは毎回異なるハッシュを作るため、文字列比較ではなくmatchesを使用します。
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return user;
    }
}
