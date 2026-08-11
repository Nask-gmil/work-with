package jp.workwith.user;

/**
 * USERSテーブルの1行をJavaで扱うためのクラスです。
 * 新規登録前はuserIdをnullにし、DB保存後は自動採番されたIDを設定します。
 */
public class User {

    private final Long userId;
    private final String username;
    private final String password;
    private final String avatarType;

    public User(Long userId, String username, String password, String avatarType) {
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.avatarType = avatarType;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getAvatarType() {
        return avatarType;
    }
}
