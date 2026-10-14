package com.example.BombStorm_Server.model;

public class Bomb {
    public String bombId;
    public String ownerId;
    public Vector2Int position;
    public int range;
    public boolean exploded = false;

    public Bomb(String bombId, String ownerId, Vector2Int position, int range) {
        this.bombId = bombId;
        this.ownerId = ownerId;
        this.position = position;
        this.range = range;
    }
}
