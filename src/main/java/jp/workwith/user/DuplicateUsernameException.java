package jp.workwith.user;

/** すでに使用されているユーザーネームで登録しようとした場合の例外です。 */
public class DuplicateUsernameException extends RuntimeException {

    public DuplicateUsernameException() {
        super("そのユーザー名はすでに使用されています");
    }
}
