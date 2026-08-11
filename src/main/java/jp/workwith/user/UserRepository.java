package jp.workwith.user;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/**
 * JdbcTemplateを使ってUSERSテーブルを読み書きするクラスです。
 * SQLの値はすべて「?」へ渡し、文字列連結によるSQLインジェクションを防ぎます。
 */
@Repository
public class UserRepository {

    private static final RowMapper<User> USER_ROW_MAPPER = (resultSet, rowNumber) -> new User(
            resultSet.getLong("user_id"),
            resultSet.getString("username"),
            resultSet.getString("password"),
            resultSet.getString("avatar_type"));

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** ユーザーを追加し、自動採番されたIDを持つUserを返します。 */
    public User create(User user) {
        String sql = "INSERT INTO USERS (username, password, avatar_type) VALUES (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, user.getUsername());
            statement.setString(2, user.getPassword());
            statement.setString(3, user.getAvatarType());
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("ユーザーIDを取得できませんでした");
        }

        return new User(
                generatedId.longValue(),
                user.getUsername(),
                user.getPassword(),
                user.getAvatarType());
    }

    /** IDが一致するユーザーを返します。存在しない場合はOptional.empty()です。 */
    public Optional<User> findById(long userId) {
        String sql = "SELECT user_id, username, password, avatar_type FROM USERS WHERE user_id = ?";
        return findOne(sql, userId);
    }

    /** ユーザーネームが一致するユーザーを返します。 */
    public Optional<User> findByUsername(String username) {
        String sql = "SELECT user_id, username, password, avatar_type FROM USERS WHERE username = ?";
        return findOne(sql, username);
    }

    /** 全ユーザーをID順で返します。 */
    public List<User> findAll() {
        String sql = "SELECT user_id, username, password, avatar_type FROM USERS ORDER BY user_id";
        return jdbcTemplate.query(sql, USER_ROW_MAPPER);
    }

    /** 指定ユーザーのアバターを変更します。更新できた場合はtrueを返します。 */
    public boolean updateAvatar(long userId, String avatarType) {
        String sql = "UPDATE USERS SET avatar_type = ? WHERE user_id = ?";
        return jdbcTemplate.update(sql, avatarType, userId) == 1;
    }

    /** 指定ユーザーを削除します。削除できた場合はtrueを返します。 */
    public boolean deleteById(long userId) {
        String sql = "DELETE FROM USERS WHERE user_id = ?";
        return jdbcTemplate.update(sql, userId) == 1;
    }

    /** 0件または1件の検索結果をOptionalへ変換する共通処理です。 */
    private Optional<User> findOne(String sql, Object parameter) {
        List<User> users = jdbcTemplate.query(sql, USER_ROW_MAPPER, parameter);
        return users.stream().findFirst();
    }
}
