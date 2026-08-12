package jp.workwith.seat;

import java.util.List;

import org.springframework.stereotype.Service;

/** 座席一覧の取得と、新しい部屋用の座席生成を担当します。 */
@Service
public class SeatService {

    private final SeatRepository seatRepository;

    public SeatService(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    public List<Seat> findByRoomId(long roomId) {
        return seatRepository.findByRoomId(roomId);
    }

    public List<Seat> createForRoom(long roomId, int maxSeats) {
        List<Seat> seats = SeatLayout.positionsFor(maxSeats).stream()
                .map(position -> new Seat(
                        null, roomId, position.seatNumber(), position.posX(), position.posY()))
                .toList();
        seatRepository.createAll(seats);
        return seatRepository.findByRoomId(roomId);
    }

    public boolean hasAvailableSeat(long roomId) {
        return seatRepository.findFirstAvailableByRoomId(roomId).isPresent();
    }
}
