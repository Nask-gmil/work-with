package jp.workwith.user;

import java.text.Normalizer;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** ユーザー登録に必要な確認とパスワードのハッシュ化を担当します。 */
@Service
public class UserService {

    public static final int MAX_USERNAME_LENGTH = 20;
    public static final int MAX_PASSWORD_LENGTH = 64;
    private static final Pattern USERNAME_PATTERN = Pattern.compile(
            "^[A-Za-z0-9_\\p{IsHiragana}\\p{IsKatakana}\\p{IsHan}]+$");

    private static final Set<String> ALLOWED_AVATAR_TYPES = Set.of(
            "male_a", "male_b", "female_a", "female_b");

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public User register(String username, String password) {
        String normalizedUsername = normalizeAndValidateUsername(username);
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("パスワードを入力してください");
        }
        if (password.length() < 8) {
            throw new IllegalArgumentException("パスワードは8文字以上で入力してください");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("パスワードは64文字以内で入力してください");
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
        String normalizedUsername = normalizeAndValidateUsername(username);
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("パスワードを入力してください");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("パスワードは64文字以内で入力してください");
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

    /** 登録・ログイン・重複検索で同じusername表現と文字種を使用します。 */
    public String normalizeAndValidateUsername(String username) {
        String trimmedUsername = username == null ? "" : username.trim();
        String normalizedUsername = Normalizer.normalize(trimmedUsername, Normalizer.Form.NFC);
        if (normalizedUsername.isEmpty()) {
            throw new IllegalArgumentException("ユーザー名を入力してください");
        }
        if (normalizedUsername.length() > MAX_USERNAME_LENGTH) {
            throw new IllegalArgumentException("ユーザー名は20文字以内で入力してください");
        }
        if (!USERNAME_PATTERN.matcher(normalizedUsername).matches()) {
            throw new IllegalArgumentException(
                    "ユーザー名は半角英数字、アンダーバー、ひらがな、カタカナ、漢字で入力してください");
        }
        return normalizedUsername;
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
