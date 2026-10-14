package com.example.BombStorm_Server.model;

public class Player {
    public String id;
    public Vector2Int position;
    public int health = 1;
    public int maxBombCount = 1;
    public int currentBombCount = 0;
    public int bombRange = 2;
    public boolean isAlive = true;

    public Player(String id,Vector2Int position) {
        this.id = id;
        this.position = position;
    }
}
