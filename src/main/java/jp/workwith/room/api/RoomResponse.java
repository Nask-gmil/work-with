package jp.workwith.room.api;

import jp.workwith.room.Room;

/** パスワードなどのユーザー情報を含めず、部屋情報だけを返すDTOです。 */
public record RoomResponse(
        Long roomId,
        String roomCode,
        String roomType,
        String roomName,
        String theme,
        String backgroundUrl,
        int maxSeats,
        Long createdBy) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getRoomId(),
                room.getRoomCode(),
                room.getRoomType(),
                room.getRoomName(),
                room.getTheme(),
                room.getBackgroundUrl(),
                room.getMaxSeats(),
                room.getCreatedBy());
    }
}
