package jp.workwith.seat;

/** SEATSテーブルの1行をJavaで扱うためのクラスです。 */
public class Seat {

    private final Long seatId;
    private final long roomId;
    private final int seatNumber;
    private final double posX;
    private final double posY;

    public Seat(Long seatId, long roomId, int seatNumber, double posX, double posY) {
        this.seatId = seatId;
        this.roomId = roomId;
        this.seatNumber = seatNumber;
        this.posX = posX;
        this.posY = posY;
    }

    public Long getSeatId() {
        return seatId;
    }

    public long getRoomId() {
        return roomId;
    }

    public int getSeatNumber() {
        return seatNumber;
    }

    public double getPosX() {
        return posX;
    }

    public double getPosY() {
        return posY;
    }
}
