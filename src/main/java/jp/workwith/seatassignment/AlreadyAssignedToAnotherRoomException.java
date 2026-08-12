package jp.workwith.seatassignment;

/** ユーザーが別の部屋へ着席済みの場合の例外です。 */
public class AlreadyAssignedToAnotherRoomException extends RuntimeException {

    public AlreadyAssignedToAnotherRoomException() {
        super("既に別の部屋に着席しています");
    }
}
