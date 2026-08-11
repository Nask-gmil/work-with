package jp.workwith.room;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

/** JdbcTemplateを使ってROOMSテーブルを読み書きします。 */
@Repository
public class RoomRepository {

    private static final String SELECT_COLUMNS =
            "room_id, room_code, room_type, room_name, theme, background_url, max_seats, created_by";

    private static final RowMapper<Room> ROOM_ROW_MAPPER = (resultSet, rowNumber) -> {
        long createdBy = resultSet.getLong("created_by");
        Long nullableCreatedBy = resultSet.wasNull() ? null : createdBy;
        return new Room(
                resultSet.getLong("room_id"),
                resultSet.getString("room_code"),
                resultSet.getString("room_type"),
                resultSet.getString("room_name"),
                resultSet.getString("theme"),
                resultSet.getString("background_url"),
                resultSet.getInt("max_seats"),
                nullableCreatedBy);
    };

    private final JdbcTemplate jdbcTemplate;

    public RoomRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 部屋を追加し、SQLiteが自動採番したroomIdを持つRoomを返します。 */
    public Room create(Room room) {
        String sql = """
                INSERT INTO ROOMS
                    (room_code, room_type, room_name, theme, background_url, max_seats, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    sql, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, room.getRoomCode());
            statement.setString(2, room.getRoomType());
            statement.setString(3, room.getRoomName());
            statement.setString(4, room.getTheme());
            statement.setString(5, room.getBackgroundUrl());
            statement.setInt(6, room.getMaxSeats());
            statement.setObject(7, room.getCreatedBy());
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("部屋IDを取得できませんでした");
        }

        return new Room(
                generatedId.longValue(),
                room.getRoomCode(),
                room.getRoomType(),
                room.getRoomName(),
                room.getTheme(),
                room.getBackgroundUrl(),
                room.getMaxSeats(),
                room.getCreatedBy());
    }

    public Optional<Room> findById(long roomId) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM ROOMS WHERE room_id = ?";
        return jdbcTemplate.query(sql, ROOM_ROW_MAPPER, roomId).stream().findFirst();
    }

    public Optional<Room> findByRoomCode(String roomCode) {
        String sql = "SELECT " + SELECT_COLUMNS + " FROM ROOMS WHERE room_code = ?";
        return jdbcTemplate.query(sql, ROOM_ROW_MAPPER, roomCode).stream().findFirst();
    }

    public List<Room> findPublicRooms() {
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM ROOMS WHERE room_type = ? ORDER BY room_id";
        return jdbcTemplate.query(sql, ROOM_ROW_MAPPER, "public");
    }

    public List<Room> findByCreatedBy(long userId) {
        String sql = "SELECT " + SELECT_COLUMNS
                + " FROM ROOMS WHERE created_by = ? ORDER BY room_id";
        return jdbcTemplate.query(sql, ROOM_ROW_MAPPER, userId);
    }

    /** テストデータの後片付けなどに使用します。 */
    public boolean deleteById(long roomId) {
        return jdbcTemplate.update("DELETE FROM ROOMS WHERE room_id = ?", roomId) == 1;
    }
}
