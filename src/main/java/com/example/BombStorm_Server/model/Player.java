package com.example.BombStorm_Server.model;

public class Player {
    public String id;
    public String name = "Player";
    public int slotIndex = 0;
    public Vector2Int position;
    public int health = 1;
    public int maxBombCount = 1;
    public int currentBombCount = 0;
    public int bombRange = 2;
    public boolean isAlive = true;

    public Player() {}

    public Player(String id, Vector2Int position) {
        this.id = id;
        this.position = position;
    }

    public Player(String id, String name, int slotIndex, Vector2Int position) {
        this.id = id;
        this.name = name;
        this.slotIndex = slotIndex;
        this.position = position;
    }
}
