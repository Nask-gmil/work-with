package jp.workwith.user.api;

/** ログイン成功時の情報です。パスワードやハッシュのフィールドは持ちません。 */
public record LoginResponse(Long userId, String username, String avatarType) {
}
