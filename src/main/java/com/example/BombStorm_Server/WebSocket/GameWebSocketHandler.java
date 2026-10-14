package com.example.BombStorm_Server.WebSocket;

import com.example.BombStorm_Server.model.Bomb;
import com.example.BombStorm_Server.model.GameMap;
import com.example.BombStorm_Server.model.Player;
import com.example.BombStorm_Server.model.Vector2Int;
import com.example.BombStorm_Server.service.MapGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final Map<String, Bomb> bombMap = new ConcurrentHashMap<>();
    private final Map<String, Player> playerMap = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MapGeneratorService mapGeneratorService;

    private volatile GameMap currentMap = null;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        System.out.println("==== Có kết nối mới! ID: " + session.getId() + " ====");

        try {
            initMap(session);
            initPlayerPosition(session);
            syncPlayerPositions(session);
        } catch (Exception e) {
            System.err.println("Lỗi khi kết nối client: " + e.getMessage());
        }
    }

    protected void initMap(WebSocketSession session) throws IOException {
        if (currentMap == null) {
            currentMap = mapGeneratorService.generateMapSession("map_01");
        }

        String mapJsonString = objectMapper.writeValueAsString(currentMap);
        String generateMapJson = "{\"action\":\"INIT_MAP\"," + mapJsonString.substring(1);
        session.sendMessage(new TextMessage((generateMapJson)));
    }

    protected void initPlayerPosition(WebSocketSession session) throws IOException {
        Vector2Int spawnPos = currentMap.GetRandomEmptyPosition();
        Player player = new Player(session.getId(), spawnPos);
        playerMap.put(player.id, player);

        broadcast(getPlayerPositionJson(player,"INIT_PLAYER"));
    }

    protected void syncPlayerPositions(WebSocketSession session) throws IOException {
        for (Player player : playerMap.values()) {
            if (!player.id.equals(session.getId())) {
                session.sendMessage(new TextMessage(getPlayerPositionJson(player, "INIT_PLAYER")));
            }
        }
    }

    protected String getPlayerPositionJson(Player player, String action) {
        Map<String, Object> data = Map.of(
                "action", action,
                "playerId", player.id,
                "x", player.position.x,
                "y", player.position.y
        );

        return objectMapper.writeValueAsString(data);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        System.out.println("Unity gửi lên: " + payload);

        try {
            ClientMessage clientMessage = objectMapper.readValue(payload, ClientMessage.class);
            String action = clientMessage.action != null ? clientMessage.action : "";

            if ("MOVE".equals(action)) {
                handelMove(clientMessage.playerId, clientMessage.direction);
            } else if ("PLACE_BOMB".equals(action)) {
                handlePlaceBomb(clientMessage.playerId);
            }
        } catch (Exception e) {
            System.out.println("Lỗi khi xử lý tin nhắn từ Client: " + e.getMessage());
        }

    }

    protected void handelMove(String playerId, String direction) throws IOException {
        Player player = playerMap.get(playerId);
        if (player == null || !player.isAlive) return;

        int nextX = player.position.x;
        int nextY = player.position.y;

        if ("UP".equals(direction)) nextY++;
        else if ("DOWN".equals(direction)) nextY--;
        else if ("LEFT".equals(direction)) nextX--;
        else if ("RIGHT".equals(direction)) nextX++;

        if (canMoveTo(nextX, nextY)) {
            player.position.x = nextX;
            player.position.y = nextY;

            String updatePositionJson = getPlayerPositionJson(player, "UPDATE_POSITION");
            broadcast(updatePositionJson);
        }
    }

    private boolean canMoveTo(int x, int y) {
        if (currentMap == null || !currentMap.InsideMap(x, y)) return false;
        int tile = currentMap.getMapData()[y * currentMap.getWidth() + x];
        return tile == 0;
    }

    private void handlePlaceBomb(String playerId) {
        Player player = playerMap.get(playerId);
        if (player == null || !player.isAlive) return;

        if (player.currentBombCount >= player.maxBombCount) return;

        int x = player.position.x;
        int y = player.position.y;
        int index = y * currentMap.getWidth() + x;

        if (currentMap.getMapData()[index] == 3) return;
        //place bomb
        currentMap.getMapData()[index] = 3;
        player.currentBombCount++;

        String bomId = UUID.randomUUID().toString();
        Bomb bomb = new Bomb(bomId, playerId, new Vector2Int(x, y), player.bombRange);
        bombMap.put(bomId, bomb);

        //async spawn bomb
        Map<String, Object> data = Map.of(
                "action", "SPAWN_BOMB",
                "bombId", bomId,
                "x", x,
                "y", y,
                "lifeTime",2f
        );
        String spawnBombJson = objectMapper.writeValueAsString(data);
        broadcast(spawnBombJson);

        //set time to explode
        scheduler.schedule(() -> explodeBomb(bomId), 2, TimeUnit.SECONDS);
    }

    private synchronized void explodeBomb(String bombId) {
        Bomb bomb = bombMap.remove(bombId);
        if (bomb == null || bomb.exploded) return;
        bomb.exploded = true;

        Player owner = playerMap.get(bomb.ownerId);
        if (owner != null) {
            owner.currentBombCount = Math.max(0, owner.currentBombCount - 1);
        }

        int startX = bomb.position.x;
        int startY = bomb.position.y;
        int width = currentMap.getWidth();

        currentMap.getMapData()[startY * width + startX] = 0;

        List<Map<String,Object>> explodedPositions = new ArrayList<>();
        explodedPositions.add(Map.of("x",startX,"y",startY));

        List<Map<String,Object>> damagedPlayers = new ArrayList<>();
        checkPlayerHit(startX, startY, damagedPlayers);

        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] dir : directions) {
            for (int r = 1; r <= bomb.range; r++) {
                int cx = startX + dir[0] * r;
                int cy = startY + dir[1] * r;

                if (!currentMap.InsideMap(cx, cy)) break;

                int index = cy * width + cx;
                int tileType = currentMap.getMapData()[index];

                if (tileType == 1) break;

                explodedPositions.add(Map.of("x",cx,"y",cy));
                checkPlayerHit(cx, cy, damagedPlayers);

                if (tileType == 2) {
                    currentMap.getMapData()[index] = 0;
                    break;
                } else if (tileType == 3) {
                    for (Bomb otherBomb : bombMap.values()) {
                        if (otherBomb.position.x == cx && otherBomb.position.y == cy) {
                            scheduler.execute(() -> explodeBomb(otherBomb.bombId));
                        }
                    }
                    break;
                }
            }
        }

        Map<String, Object> data = Map.of(
                "action", "EXPLODE_BOMB",
                "bombId", bombId,
                "explodedCells", explodedPositions,
                "damagedPlayers", damagedPlayers
        );
        String json = objectMapper.writeValueAsString(data);
        broadcast(json);
    }

    private String getPositionJson(int x, int y) {
        return String.format("{\"x\":%d,\"y\":%d}", x, y);
    }

    private void checkPlayerHit(int x, int y, List<Map<String,Object>> damagedPlayers) {
        for (Player player : playerMap.values()) {
            if (player.isAlive && player.position.x == x && player.position.y == y) {
                player.health--;
                if (player.health <= 0) {
                    player.isAlive = false;
                }
                Map<String, Object> data = Map.of(
                        "playerId", player.id,
                        "health", player.health,
                        "isAlive", player.isAlive
                );
                damagedPlayers.add(data);
            }
        }
    }

    private void broadcast(String message) {
        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (Exception e) {
                    System.err.println("Lỗi gửi tin: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        System.out.println("==== Người chơi ID " + session.getId() + " đã thoát ====");
    }

    static class ClientMessage {
        public String action;
        public String direction;
        public String playerId;
    }
}
