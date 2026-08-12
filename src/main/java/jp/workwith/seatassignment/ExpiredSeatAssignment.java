package jp.workwith.seatassignment;

import java.time.LocalDateTime;

/** heartbeat timeoutの削除候補と、その座席が属する部屋を表します。 */
public record ExpiredSeatAssignment(
        long seatId,
        long roomId,
        LocalDateTime lastHeartbeatAt) {
}
