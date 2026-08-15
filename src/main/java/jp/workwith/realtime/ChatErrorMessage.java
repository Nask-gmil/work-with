package jp.workwith.realtime;

public record ChatErrorMessage(String type, String message, long retryAfterSeconds) {
}
