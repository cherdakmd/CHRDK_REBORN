package ru.example.vkchatoffline.data;

import java.util.UUID;

public class ActiveShift {
    private final int peerId;
    private final int vkId;
    private final UUID playerUuid;
    private final String shiftType;
    private final int hours;
    private final long endTime;

    public ActiveShift(int peerId, int vkId, UUID playerUuid, String shiftType, int hours, long endTime) {
        this.peerId = peerId;
        this.vkId = vkId;
        this.playerUuid = playerUuid;
        this.shiftType = shiftType;
        this.hours = hours;
        this.endTime = endTime;
    }

    public int getPeerId() { return peerId; }
    public int getVkId() { return vkId; }
    public UUID getPlayerUuid() { return playerUuid; }
    public String getShiftType() { return shiftType; }
    public int getHours() { return hours; }
    public long getEndTime() { return endTime; }
}
