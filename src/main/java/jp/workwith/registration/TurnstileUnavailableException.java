package jp.workwith.registration;

/** Turnstile設定不足またはSiteverify通信失敗を表します。内部詳細は利用者へ返しません。 */
public class TurnstileUnavailableException extends RuntimeException {

    public TurnstileUnavailableException(Throwable cause) {
        super(cause);
    }

    public TurnstileUnavailableException() {
        super();
    }
}
