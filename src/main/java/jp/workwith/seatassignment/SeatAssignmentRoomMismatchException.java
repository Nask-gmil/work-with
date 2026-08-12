package jp.workwith.seatassignment;

/** 退出要求の部屋と、ユーザーが実際に着席している部屋が異なる場合の例外です。 */
public class SeatAssignmentRoomMismatchException extends RuntimeException {

    public SeatAssignmentRoomMismatchException() {
        super("現在参加している部屋と退出対象の部屋が一致しません");
    }
}
