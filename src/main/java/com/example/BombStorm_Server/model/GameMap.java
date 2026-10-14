package com.example.BombStorm_Server.model;

import java.util.Random;

public class GameMap {
    private String id;
    private String name;
    private int width;
    private int height;
    private int boxAmount;

    private int[] mapData;

    public GameMap() {
    }

    public GameMap(String id, String name, int width, int height, int boxAmount, int[] mapData) {
        this.id = id;
        this.name = name;
        this.width = width;
        this.height = height;
        this.boxAmount = boxAmount;
        this.mapData = mapData;
    }

    public Vector2Int GetRandomEmptyPosition(){
        int attemps = 1000;
        Random random = new Random();
        while(attemps>0){
            attemps--;

            int x = random.nextInt(width);
            int y = random.nextInt(height);

            if(InsideMap(x,y) && mapData[y*width+x]==0)
               return new Vector2Int(x,y);
        }

        return null;
    }

    public boolean InsideMap(int x,int y){
        if(x<0 || x>= width || y<0 || y>=height)
            return false;
        return true;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }

    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public int getBoxAmount() { return boxAmount; }
    public void setBoxAmount(int boxAmount) { this.boxAmount = boxAmount; }

    public int[] getMapData() { return mapData; }
    public void setMapData(int[] mapData) { this.mapData = mapData; }
}
