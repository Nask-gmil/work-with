package jp.workwith.user.api;

/** 登録APIが受け取るJSONを表します。パスワードを含むためtoStringは実装しません。 */
public class UserRegistrationRequest {

    private String username;
    private String password;

    public UserRegistrationRequest() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
