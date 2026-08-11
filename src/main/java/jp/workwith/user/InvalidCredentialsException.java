package jp.workwith.user;

/** ユーザー名またはパスワードが一致しない場合の例外です。 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("ユーザー名またはパスワードが正しくありません。");
    }
}
