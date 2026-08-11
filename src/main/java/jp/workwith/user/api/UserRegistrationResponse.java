package jp.workwith.user.api;

/** 登録成功時のレスポンスです。パスワードはフィールド自体を持ちません。 */
public record UserRegistrationResponse(Long userId, String username) {
}
