package jp.workwith.seat;

import java.util.List;

/**
 * ワークスペース画面で調整済みの10席の座標を一か所で管理します。
 * posX/posYは部屋表示エリアに対するパーセント値です。
 */
public final class SeatLayout {

    public record Position(int seatNumber, double posX, double posY) {
    }

    private static final List<Position> POSITIONS = List.of(
            new Position(1, 26.0, 31.6),
            new Position(2, 37.0, 35.0),
            new Position(3, 48.0, 38.0),
            new Position(4, 58.8, 40.8),
            new Position(5, 69.0, 44.0),
            new Position(6, 22.0, 56.0),
            new Position(7, 32.0, 59.0),
            new Position(8, 43.0, 62.0),
            new Position(9, 54.0, 65.0),
            new Position(10, 65.0, 69.0));

    private SeatLayout() {
    }

    public static List<Position> positionsFor(int maxSeats) {
        if (maxSeats < 1 || maxSeats > POSITIONS.size()) {
            throw new IllegalArgumentException("座席数は1〜10の範囲で指定してください");
        }
        return List.copyOf(POSITIONS.subList(0, maxSeats));
    }
}
