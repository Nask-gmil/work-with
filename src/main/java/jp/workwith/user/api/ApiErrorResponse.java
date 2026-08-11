package jp.workwith.user.api;

/** 登録失敗の理由を画面へ返すレスポンスです。 */
public record ApiErrorResponse(String message) {
}
