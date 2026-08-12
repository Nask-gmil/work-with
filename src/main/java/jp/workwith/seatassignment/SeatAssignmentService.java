package jp.workwith.seatassignment;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jp.workwith.room.RoomNotFoundException;
import jp.workwith.room.RoomRepository;
import jp.workwith.seat.Seat;
import jp.workwith.seat.SeatRepository;

/** 現在の座席割り当てを取得します。 */
@Service
public class SeatAssignmentService {

    private final SeatAssignmentRepository seatAssignmentRepository;
    private final SeatRepository seatRepository;
    private final RoomRepository roomRepository;

    public SeatAssignmentService(
            SeatAssignmentRepository seatAssignmentRepository,
            SeatRepository seatRepository,
            RoomRepository roomRepository) {
        this.seatAssignmentRepository = seatAssignmentRepository;
        this.seatRepository = seatRepository;
        this.roomRepository = roomRepository;
    }

    public List<SeatAssignment> findByRoomId(long roomId) {
        return seatAssignmentRepository.findByRoomId(roomId);
    }

    public List<RoomParticipant> findParticipantsByRoomId(long roomId) {
        return seatAssignmentRepository.findParticipantsByRoomId(roomId);
    }

    /** 指定部屋の座席番号が最も小さい空席へ、ユーザーを自動で割り当てます。 */
    @Transactional
    public SeatAssignment autoAssignSeat(long roomId, long userId) {
        roomRepository.findById(roomId).orElseThrow(RoomNotFoundException::new);

        Optional<SeatAssignment> existingAssignment =
                seatAssignmentRepository.findByUserId(userId);
        if (existingAssignment.isPresent()) {
            return validateExistingAssignment(roomId, existingAssignment.get());
        }

        while (true) {
            Seat availableSeat = seatRepository.findFirstAvailableByRoomId(roomId)
                    .orElseThrow(RoomFullException::new);
            // SQLite/JDBCのDATETIME保存精度に揃え、保存前後で同じ時刻値にします。
            LocalDateTime assignedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
            SeatAssignment assignment = new SeatAssignment(
                    availableSeat.getSeatId(),
                    userId,
                    "working",
                    null,
                    assignedAt,
                    assignedAt);

            try {
                seatAssignmentRepository.create(assignment);
                return assignment;
            } catch (DataIntegrityViolationException exception) {
                // 同時処理で同じ席または同じユーザーが先に登録された場合を確認します。
                Optional<SeatAssignment> concurrentAssignment =
                        seatAssignmentRepository.findByUserId(userId);
                if (concurrentAssignment.isPresent()) {
                    return validateExistingAssignment(roomId, concurrentAssignment.get());
                }
                // 席だけが先に使われた場合は、次に小さい空席を探し直します。
            }
        }
    }

    /** URLの部屋と現在の着席先が一致するときだけ、本人の割り当てを削除します。 */
    @Transactional
    public boolean leaveRoom(long roomId, long userId) {
        roomRepository.findById(roomId).orElseThrow(RoomNotFoundException::new);
        Optional<SeatAssignment> assignment = seatAssignmentRepository.findByUserId(userId);
        if (assignment.isEmpty()) {
            return false;
        }

        Seat assignedSeat = seatRepository.findById(assignment.get().getSeatId())
                .orElseThrow(() -> new IllegalStateException("着席中の座席が見つかりません"));
        if (assignedSeat.getRoomId() != roomId) {
            throw new SeatAssignmentRoomMismatchException();
        }
        return seatAssignmentRepository.deleteBySeatId(assignment.get().getSeatId());
    }

    @Transactional
    public RoomParticipant updateStatus(long roomId, long userId, String status) {
        if (!"working".equals(status) && !"break".equals(status)) {
            throw new IllegalArgumentException("statusはworkingまたはbreakを指定してください");
        }
        roomRepository.findById(roomId).orElseThrow(RoomNotFoundException::new);

        SeatAssignment assignment = seatAssignmentRepository.findByUserId(userId)
                .orElseThrow(SeatAssignmentNotFoundException::new);
        Seat assignedSeat = seatRepository.findById(assignment.getSeatId())
                .orElseThrow(() -> new IllegalStateException("着席中の座席が見つかりません"));
        if (assignedSeat.getRoomId() != roomId) {
            throw new SeatAssignmentRoomMismatchException();
        }
        if (!seatAssignmentRepository.updateStatusBySeatId(assignment.getSeatId(), status)) {
            throw new IllegalStateException("状態を更新する座席割り当てが見つかりません");
        }

        return seatAssignmentRepository.findParticipantsByRoomId(roomId).stream()
                .filter(participant -> participant.userId() == userId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("更新した座席割り当てが見つかりません"));
    }

    private SeatAssignment validateExistingAssignment(
            long requestedRoomId,
            SeatAssignment existingAssignment) {
        long assignedRoomId = seatRepository.findById(existingAssignment.getSeatId())
                .orElseThrow(() -> new IllegalStateException("着席中の座席が見つかりません"))
                .getRoomId();
        if (assignedRoomId != requestedRoomId) {
            throw new AlreadyAssignedToAnotherRoomException();
        }
        return existingAssignment;
    }
}
