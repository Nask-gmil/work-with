package jp.workwith.seatassignment;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/** JdbcTemplateでSEAT_ASSIGNMENTSテーブルを読み書きします。 */
@Repository
public class SeatAssignmentRepository {

    private static final String SELECT_COLUMNS =
            "sa.seat_id, sa.user_id, sa.status, sa.work_content, "
                    + "sa.started_at, sa.last_heartbeat_at";

    private static final RowMapper<SeatAssignment> SEAT_ASSIGNMENT_ROW_MAPPER =
            (resultSet, rowNumber) -> new SeatAssignment(
                    resultSet.getLong("seat_id"),
                    resultSet.getLong("user_id"),
                    resultSet.getString("status"),
                    resultSet.getString("work_content"),
                    toLocalDateTime(resultSet.getTimestamp("started_at")),
                    toLocalDateTime(resultSet.getTimestamp("last_heartbeat_at")));

    private final JdbcTemplate jdbcTemplate;

    public SeatAssignmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(SeatAssignment assignment) {
        jdbcTemplate.update(
                """
                INSERT INTO SEAT_ASSIGNMENTS
                    (seat_id, user_id, status, work_content, started_at, last_heartbeat_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                assignment.getSeatId(),
                assignment.getUserId(),
                assignment.getStatus(),
                assignment.getWorkContent(),
                toTimestamp(assignment.getStartedAt()),
                toTimestamp(assignment.getLastHeartbeatAt()));
    }

    public Optional<SeatAssignment> findBySeatId(long seatId) {
        return findOne("SELECT " + SELECT_COLUMNS
                + " FROM SEAT_ASSIGNMENTS sa WHERE sa.seat_id = ?", seatId);
    }

    public Optional<SeatAssignment> findByUserId(long userId) {
        return findOne("SELECT " + SELECT_COLUMNS
                + " FROM SEAT_ASSIGNMENTS sa WHERE sa.user_id = ?", userId);
    }

    /** SEATSを経由し、座席番号順で指定部屋の割り当てを返します。 */
    public List<SeatAssignment> findByRoomId(long roomId) {
        return jdbcTemplate.query(
                """
                SELECT sa.seat_id, sa.user_id, sa.status, sa.work_content,
                       sa.started_at, sa.last_heartbeat_at
                FROM SEAT_ASSIGNMENTS sa
                JOIN SEATS s ON s.seat_id = sa.seat_id
                WHERE s.room_id = ?
                ORDER BY s.seat_number
                """,
                SEAT_ASSIGNMENT_ROW_MAPPER,
                roomId);
    }

    /** USERSを一度だけJOINし、部屋画面に必要な着席ユーザー情報を返します。 */
    public List<RoomParticipant> findParticipantsByRoomId(long roomId) {
        return jdbcTemplate.query(
                """
                SELECT sa.seat_id, sa.user_id, u.username, u.avatar_type,
                       sa.status, sa.work_content, sa.started_at, sa.last_heartbeat_at
                FROM SEAT_ASSIGNMENTS sa
                JOIN SEATS s ON s.seat_id = sa.seat_id
                JOIN USERS u ON u.user_id = sa.user_id
                WHERE s.room_id = ?
                ORDER BY s.seat_number
                """,
                (resultSet, rowNumber) -> new RoomParticipant(
                        resultSet.getLong("seat_id"),
                        resultSet.getLong("user_id"),
                        resultSet.getString("username"),
                        resultSet.getString("avatar_type"),
                        resultSet.getString("status"),
                        resultSet.getString("work_content"),
                        toLocalDateTime(resultSet.getTimestamp("started_at")),
                        toLocalDateTime(resultSet.getTimestamp("last_heartbeat_at"))),
                roomId);
    }

    public boolean deleteBySeatId(long seatId) {
        return jdbcTemplate.update(
                "DELETE FROM SEAT_ASSIGNMENTS WHERE seat_id = ?", seatId) == 1;
    }

    public boolean updateStatusBySeatId(long seatId, String status) {
        return jdbcTemplate.update(
                "UPDATE SEAT_ASSIGNMENTS SET status = ? WHERE seat_id = ?",
                status,
                seatId) == 1;
    }

    private Optional<SeatAssignment> findOne(String sql, long parameter) {
        return jdbcTemplate.query(sql, SEAT_ASSIGNMENT_ROW_MAPPER, parameter)
                .stream().findFirst();
    }

    private static Timestamp toTimestamp(LocalDateTime value) {
        return value == null ? null : Timestamp.valueOf(value);
    }

    private static LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
