package jp.workwith.user;

import java.util.Optional;
import java.util.Set;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** ユーザー登録に必要な確認とパスワードのハッシュ化を担当します。 */
@Service
public class UserService {

    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of(
            "male_a", "male_b", "female_a", "female_b");

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

    /** セッションに保存されたIDから現在のユーザーを取得します。 */
    public Optional<User> findById(long userId) {
        return userRepository.findById(userId);
    }

    /** ログイン中のユーザーのアバターを検証して更新します。 */
    public User updateAvatar(long userId, String avatarType) {
        String normalizedAvatarType = avatarType == null ? "" : avatarType.trim();

        if (normalizedAvatarType.isEmpty()) {
            throw new IllegalArgumentException("アバターを選択してください");
        }
        if (!ALLOWED_AVATAR_TYPES.contains(normalizedAvatarType)) {
            throw new IllegalArgumentException("指定されたアバターは使用できません");
        }
        if (userRepository.findById(userId).isEmpty()) {
            throw new UserNotFoundException();
        }

        if (!userRepository.updateAvatar(userId, normalizedAvatarType)) {
            throw new UserNotFoundException();
        }

        return userRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
    }
}
