package jp.workwith.seatassignment;

/** 自動着席しようとした部屋に空席がない場合の例外です。 */
public class RoomFullException extends RuntimeException {

    public RoomFullException() {
        super("部屋は満席です");
    }
}
