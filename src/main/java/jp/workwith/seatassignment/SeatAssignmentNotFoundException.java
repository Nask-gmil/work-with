package jp.workwith.seatassignment;

public class SeatAssignmentNotFoundException extends RuntimeException {

    public SeatAssignmentNotFoundException() {
        super("現在この部屋に着席していません");
    }
}
