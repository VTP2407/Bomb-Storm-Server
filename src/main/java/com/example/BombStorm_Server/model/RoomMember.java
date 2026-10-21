package com.example.BombStorm_Server.model;

public class RoomMember {
    public String playerId;
    public String playerName;
    public int slotIndex; // 0, 1, 2, 3
    public boolean isHost;
    public boolean isReady;

    public RoomMember() {}

    public RoomMember(String playerId, String playerName, int slotIndex, boolean isHost, boolean isReady) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.slotIndex = slotIndex;
        this.isHost = isHost;
        this.isReady = isReady;
    }
}
