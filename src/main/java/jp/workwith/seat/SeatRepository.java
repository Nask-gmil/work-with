package jp.workwith.seat;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** JdbcTemplateでSEATSテーブルを読み書きします。 */
@Repository
public class SeatRepository {

    private static final RowMapper<Seat> SEAT_ROW_MAPPER = (resultSet, rowNumber) -> new Seat(
            resultSet.getLong("seat_id"),
            resultSet.getLong("room_id"),
            resultSet.getInt("seat_number"),
            resultSet.getDouble("pos_x"),
            resultSet.getDouble("pos_y"));

    private final JdbcTemplate jdbcTemplate;

    public SeatRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 1件追加します。IDが必要な処理ではfindByRoomIdなどで取得します。 */
    public void create(Seat seat) {
        jdbcTemplate.update(
                "INSERT INTO SEATS (room_id, seat_number, pos_x, pos_y) VALUES (?, ?, ?, ?)",
                seat.getRoomId(), seat.getSeatNumber(), seat.getPosX(), seat.getPosY());
    }

    /** 部屋作成時の複数座席を一括追加します。 */
    public void createAll(List<Seat> seats) {
        jdbcTemplate.batchUpdate(
                "INSERT INTO SEATS (room_id, seat_number, pos_x, pos_y) VALUES (?, ?, ?, ?)",
                seats,
                seats.size(),
                (statement, seat) -> {
                    statement.setLong(1, seat.getRoomId());
                    statement.setInt(2, seat.getSeatNumber());
                    statement.setDouble(3, seat.getPosX());
                    statement.setDouble(4, seat.getPosY());
                });
    }

    public Optional<Seat> findById(long seatId) {
        return jdbcTemplate.query(
                "SELECT seat_id, room_id, seat_number, pos_x, pos_y FROM SEATS WHERE seat_id = ?",
                SEAT_ROW_MAPPER,
                seatId).stream().findFirst();
    }

    /** seat_numberの昇順で、指定した部屋の座席を返します。 */
    public List<Seat> findByRoomId(long roomId) {
        return jdbcTemplate.query(
                """
                SELECT seat_id, room_id, seat_number, pos_x, pos_y
                FROM SEATS
                WHERE room_id = ?
                ORDER BY seat_number
                """,
                SEAT_ROW_MAPPER,
                roomId);
    }

    /** テストデータの後片付けなど、部屋の全座席を削除するときに使用します。 */
    public int deleteByRoomId(long roomId) {
        return jdbcTemplate.update("DELETE FROM SEATS WHERE room_id = ?", roomId);
    }
}
