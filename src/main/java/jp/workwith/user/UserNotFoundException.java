package jp.workwith.user;

/** セッション内のIDに対応するユーザーが存在しない場合の例外です。 */
public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException() {
        super("ログイン中のユーザーが見つかりません");
    }
}
