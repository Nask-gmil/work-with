package jp.workwith.seatassignment;

import java.time.LocalDateTime;

/** SEAT_ASSIGNMENTSテーブルの1行をJavaで扱うためのクラスです。 */
public class SeatAssignment {

    private final long seatId;
    private final long userId;
    private final String status;
    private final String workContent;
    private final LocalDateTime startedAt;
    private final LocalDateTime lastHeartbeatAt;

    public SeatAssignment(
            long seatId,
            long userId,
            String status,
            String workContent,
            LocalDateTime startedAt,
            LocalDateTime lastHeartbeatAt) {
        this.seatId = seatId;
        this.userId = userId;
        this.status = status;
        this.workContent = workContent;
        this.startedAt = startedAt;
        this.lastHeartbeatAt = lastHeartbeatAt;
    }

    public long getSeatId() { return seatId; }
    public long getUserId() { return userId; }
    public String getStatus() { return status; }
    public String getWorkContent() { return workContent; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getLastHeartbeatAt() { return lastHeartbeatAt; }
}
