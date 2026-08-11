package jp.workwith.seat.api;

import jp.workwith.seat.Seat;

/** APIには画面表示に必要な座席情報だけを返します。 */
public record SeatResponse(long seatId, int seatNumber, double posX, double posY) {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(
                seat.getSeatId(), seat.getSeatNumber(), seat.getPosX(), seat.getPosY());
    }
}
