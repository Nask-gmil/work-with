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
