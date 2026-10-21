package com.example.BombStorm_Server.WebSocket;

import com.example.BombStorm_Server.entity.MatchHistoryEntity;
import com.example.BombStorm_Server.entity.UserEntity;
import com.example.BombStorm_Server.model.*;
import com.example.BombStorm_Server.repository.MatchHistoryRepository;
import com.example.BombStorm_Server.repository.UserRepository;
import com.example.BombStorm_Server.service.JwtService;
import com.example.BombStorm_Server.service.MapGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private final CopyOnWriteArrayList<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> sessionToRoomId = new ConcurrentHashMap<>();
    private final Map<String, UserEntity> sessionUsers = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MapGeneratorService mapGeneratorService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MatchHistoryRepository matchHistoryRepository;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        System.out.println("==== Có kết nối mới! ID: " + session.getId() + " ====");
        try {
            sendRoomList(session);
        } catch (Exception e) {
            System.err.println("Lỗi gửi Room List khi kết nối: " + e.getMessage());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        try {
            ClientMessage msg = objectMapper.readValue(payload, ClientMessage.class);
            String action = msg.action != null ? msg.action : "";

            switch (action) {
                case "AUTH":
                    handleAuth(session, msg.token);
                    break;
                case "GET_ROOM_LIST":
                    sendRoomList(session);
                    break;
                case "CREATE_ROOM":
                    handleCreateRoom(session, msg);
                    break;
                case "JOIN_ROOM":
                    handleJoinRoom(session, msg);
                    break;
                case "LEAVE_ROOM":
                    handleLeaveRoom(session);
                    break;
                case "TOGGLE_READY":
                    handleToggleReady(session);
                    break;
                case "START_GAME":
                    handleStartGame(session);
                    break;
                case "MOVE":
                    handleMove(session, msg.playerId, msg.direction);
                    break;
                case "PLACE_BOMB":
                    handlePlaceBomb(session, msg.playerId);
                    break;
                case "HELLO":
                    sendRoomList(session);
                    break;
                default:
                    System.out.println("Action: " + action);
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi xử lý tin nhắn từ client: " + e.getMessage());
        }
    }

    private void handleAuth(WebSocketSession session, String token) {
        if (token == null || !jwtService.isValid(token)) {
            sendError(session, "Token xác thực không hợp lệ hoặc đã hết hạn!");
            return;
        }

        Long userId = jwtService.getUserIdFromToken(token);
        if (userId != null) {
            userRepository.findById(userId).ifPresent(user -> {
                sessionUsers.put(session.getId(), user);
                try {
                    Map<String, Object> data = Map.of(
                            "action", "AUTH_SUCCESS",
                            "userId", user.getId(),
                            "username", user.getUsername(),
                            "displayName", user.getDisplayName(),
                            "score", user.getScore()
                    );
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
                    System.out.println("Session " + session.getId() + " đã xác thực với tài khoản: " + user.getUsername());
                } catch (IOException ignored) {}
            });
        }
    }

    private void handleCreateRoom(WebSocketSession session, ClientMessage msg) throws IOException {
        if (sessionToRoomId.containsKey(session.getId())) {
            handleLeaveRoom(session);
        }

        String roomId = generateUniqueRoomId();
        String roomName = (msg.roomName != null && !msg.roomName.trim().isEmpty())
                ? msg.roomName.trim()
                : "Phòng #" + roomId;
        int maxPlayers = (msg.maxPlayers >= 2 && msg.maxPlayers <= 4) ? msg.maxPlayers : 4;

        // Use authenticated displayName if available
        String playerName = "Chủ Phòng";
        UserEntity authUser = sessionUsers.get(session.getId());
        if (authUser != null) {
            playerName = authUser.getDisplayName();
        } else if (msg.playerName != null && !msg.playerName.trim().isEmpty()) {
            playerName = msg.playerName.trim();
        }

        Room room = new Room(roomId, roomName, maxPlayers, session.getId());
        room.addMember(session, playerName);

        rooms.put(roomId, room);
        sessionToRoomId.put(session.getId(), roomId);

        System.out.println("Tạo phòng mới: " + roomId + " (" + roomName + ") bởi " + playerName);

        Map<String, Object> joinedData = Map.of(
                "action", "ROOM_JOINED",
                "success", true,
                "roomId", room.roomId,
                "roomName", room.roomName,
                "isHost", true,
                "myPlayerId", session.getId(),
                "members", room.getMembersList()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(joinedData)));

        broadcastRoomList();
    }

    private void handleJoinRoom(WebSocketSession session, ClientMessage msg) throws IOException {
        String roomId = msg.roomId != null ? msg.roomId.trim() : "";
        Room room = rooms.get(roomId);

        if (room == null) {
            sendError(session, "Phòng không tồn tại hoặc đã bị đóng!");
            return;
        }

        if (room.status != RoomStatus.LOBBY) {
            sendError(session, "Trận đấu trong phòng này đã bắt đầu!");
            return;
        }

        if (room.members.size() >= room.maxPlayers) {
            sendError(session, "Phòng đã đầy người chơi!");
            return;
        }

        if (sessionToRoomId.containsKey(session.getId())) {
            handleLeaveRoom(session);
        }

        // Use authenticated displayName if available
        String playerName = "Thành viên " + (room.members.size() + 1);
        UserEntity authUser = sessionUsers.get(session.getId());
        if (authUser != null) {
            playerName = authUser.getDisplayName();
        } else if (msg.playerName != null && !msg.playerName.trim().isEmpty()) {
            playerName = msg.playerName.trim();
        }

        boolean added = room.addMember(session, playerName);
        if (!added) {
            sendError(session, "Không thể tham gia phòng!");
            return;
        }

        sessionToRoomId.put(session.getId(), roomId);

        System.out.println("Player " + playerName + " đã vào phòng: " + roomId);

        Map<String, Object> joinedData = Map.of(
                "action", "ROOM_JOINED",
                "success", true,
                "roomId", room.roomId,
                "roomName", room.roomName,
                "isHost", session.getId().equals(room.hostId),
                "myPlayerId", session.getId(),
                "members", room.getMembersList()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(joinedData)));

        broadcastRoomUpdate(room);
        broadcastRoomList();
    }

    private void handleLeaveRoom(WebSocketSession session) {
        String roomId = sessionToRoomId.remove(session.getId());
        if (roomId == null) return;

        Room room = rooms.get(roomId);
        if (room == null) return;

        boolean isEmpty = room.removeMember(session.getId());

        if (session.isOpen()) {
            try {
                Map<String, Object> leftData = Map.of(
                        "action", "ROOM_LEFT",
                        "success", true
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(leftData)));
            } catch (Exception ignored) {}
        }

        if (isEmpty) {
            rooms.remove(roomId);
        } else {
            broadcastRoomUpdate(room);
        }

        broadcastRoomList();
    }

    private void handleToggleReady(WebSocketSession session) {
        String roomId = sessionToRoomId.get(session.getId());
        if (roomId == null) return;

        Room room = rooms.get(roomId);
        if (room == null || room.status != RoomStatus.LOBBY) return;

        RoomMember member = room.members.get(session.getId());
        if (member != null && !member.isHost) {
            member.isReady = !member.isReady;
            broadcastRoomUpdate(room);
        }
    }

    private void handleStartGame(WebSocketSession session) throws IOException {
        String roomId = sessionToRoomId.get(session.getId());
        if (roomId == null) return;

        Room room = rooms.get(roomId);
        if (room == null || room.status != RoomStatus.LOBBY) return;

        if (!session.getId().equals(room.hostId)) {
            sendError(session, "Chỉ có chủ phòng mới có quyền bắt đầu trận đấu!");
            return;
        }

        if (room.members.size() > 1 && !room.isAllReady()) {
            sendError(session, "Chưa thể bắt đầu: Vẫn còn thành viên chưa bấm Sẵn Sàng!");
            return;
        }

        room.status = RoomStatus.IN_GAME;
        room.currentMap = mapGeneratorService.generateMapSession("map_01");

        int width = room.currentMap.getWidth();
        int height = room.currentMap.getHeight();
        int[] mapData = room.currentMap.getMapData();

        clearCorner(mapData, width, height, 1, 1);
        clearCorner(mapData, width, height, width - 2, height - 2);
        clearCorner(mapData, width, height, 1, height - 2);
        clearCorner(mapData, width, height, width - 2, 1);

        Vector2Int[] cornerSpawns = new Vector2Int[]{
                new Vector2Int(1, 1),
                new Vector2Int(width - 2, height - 2),
                new Vector2Int(1, height - 2),
                new Vector2Int(width - 2, 1)
        };

        room.playerMap.clear();
        room.bombMap.clear();

        for (RoomMember member : room.getMembersList()) {
            Vector2Int spawn = (member.slotIndex >= 0 && member.slotIndex < cornerSpawns.length)
                    ? cornerSpawns[member.slotIndex]
                    : room.currentMap.GetRandomEmptyPosition();

            Player player = new Player(member.playerId, member.playerName, member.slotIndex, spawn);
            room.playerMap.put(player.id, player);
        }

        Map<String, Object> mapMsg = Map.of(
                "action", "INIT_MAP",
                "roomId", room.roomId,
                "width", width,
                "height", height,
                "mapData", mapData
        );
        room.broadcast(objectMapper.writeValueAsString(mapMsg));

        for (Player p : room.playerMap.values()) {
            Map<String, Object> pMsg = Map.of(
                    "action", "INIT_PLAYER",
                    "roomId", room.roomId,
                    "playerId", p.id,
                    "playerName", p.name,
                    "slotIndex", p.slotIndex,
                    "x", p.position.x,
                    "y", p.position.y
            );
            room.broadcast(objectMapper.writeValueAsString(pMsg));
        }

        broadcastRoomList();
    }

    private void clearCorner(int[] mapData, int width, int height, int cx, int cy) {
        int[] dx = {0, 0, 1, -1};
        int[] dy = {0, 1, 0, 0};
        for (int i = 0; i < 4; i++) {
            int x = cx + dx[i];
            int y = cy + dy[i];
            if (x >= 0 && x < width && y >= 0 && y < height) {
                int index = y * width + x;
                if (mapData[index] == 2) {
                    mapData[index] = 0;
                }
            }
        }
    }

    private void handleMove(WebSocketSession session, String playerId, String direction) throws IOException {
        String roomId = sessionToRoomId.get(session.getId());
        if (roomId == null) return;

        Room room = rooms.get(roomId);
        if (room == null || room.status != RoomStatus.IN_GAME) return;

        Player player = room.playerMap.get(playerId);
        if (player == null || !player.isAlive) return;

        int nextX = player.position.x;
        int nextY = player.position.y;

        if ("UP".equals(direction)) nextY++;
        else if ("DOWN".equals(direction)) nextY--;
        else if ("LEFT".equals(direction)) nextX--;
        else if ("RIGHT".equals(direction)) nextX++;

        if (canMoveTo(room.currentMap, nextX, nextY)) {
            player.position.x = nextX;
            player.position.y = nextY;

            Map<String, Object> data = Map.of(
                    "action", "UPDATE_POSITION",
                    "playerId", player.id,
                    "x", player.position.x,
                    "y", player.position.y
            );
            room.broadcast(objectMapper.writeValueAsString(data));
        }
    }

    private boolean canMoveTo(GameMap map, int x, int y) {
        if (map == null || !map.InsideMap(x, y)) return false;
        int tile = map.getMapData()[y * map.getWidth() + x];
        return tile == 0;
    }

    private void handlePlaceBomb(WebSocketSession session, String playerId) throws IOException {
        String roomId = sessionToRoomId.get(session.getId());
        if (roomId == null) return;

        Room room = rooms.get(roomId);
        if (room == null || room.status != RoomStatus.IN_GAME) return;

        Player player = room.playerMap.get(playerId);
        if (player == null || !player.isAlive) return;

        if (player.currentBombCount >= player.maxBombCount) return;

        int x = player.position.x;
        int y = player.position.y;
        int index = y * room.currentMap.getWidth() + x;

        if (room.currentMap.getMapData()[index] == 3) return;

        room.currentMap.getMapData()[index] = 3;
        player.currentBombCount++;

        String bombId = UUID.randomUUID().toString();
        Bomb bomb = new Bomb(bombId, playerId, new Vector2Int(x, y), player.bombRange);
        room.bombMap.put(bombId, bomb);

        Map<String, Object> data = Map.of(
                "action", "SPAWN_BOMB",
                "bombId", bombId,
                "x", x,
                "y", y,
                "lifeTime", 2f
        );
        room.broadcast(objectMapper.writeValueAsString(data));

        scheduler.schedule(() -> explodeBomb(roomId, bombId), 2, TimeUnit.SECONDS);
    }

    private synchronized void explodeBomb(String roomId, String bombId) {
        Room room = rooms.get(roomId);
        if (room == null || room.status != RoomStatus.IN_GAME) return;

        Bomb bomb = room.bombMap.remove(bombId);
        if (bomb == null || bomb.exploded) return;
        bomb.exploded = true;

        Player owner = room.playerMap.get(bomb.ownerId);
        if (owner != null) {
            owner.currentBombCount = Math.max(0, owner.currentBombCount - 1);
        }

        int startX = bomb.position.x;
        int startY = bomb.position.y;
        int width = room.currentMap.getWidth();

        room.currentMap.getMapData()[startY * width + startX] = 0;

        List<Map<String, Object>> explodedPositions = new ArrayList<>();
        explodedPositions.add(Map.of("x", startX, "y", startY));

        List<Map<String, Object>> damagedPlayers = new ArrayList<>();
        checkPlayerHit(room, startX, startY, damagedPlayers);

        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

        for (int[] dir : directions) {
            for (int r = 1; r <= bomb.range; r++) {
                int cx = startX + dir[0] * r;
                int cy = startY + dir[1] * r;

                if (!room.currentMap.InsideMap(cx, cy)) break;

                int index = cy * width + cx;
                int tileType = room.currentMap.getMapData()[index];

                if (tileType == 1) break;

                explodedPositions.add(Map.of("x", cx, "y", cy));
                checkPlayerHit(room, cx, cy, damagedPlayers);

                if (tileType == 2) {
                    room.currentMap.getMapData()[index] = 0;
                    break;
                } else if (tileType == 3) {
                    for (Bomb otherBomb : room.bombMap.values()) {
                        if (otherBomb.position.x == cx && otherBomb.position.y == cy) {
                            scheduler.execute(() -> explodeBomb(roomId, otherBomb.bombId));
                        }
                    }
                    break;
                }
            }
        }

        try {
            Map<String, Object> data = Map.of(
                    "action", "EXPLODE_BOMB",
                    "bombId", bombId,
                    "explodedCells", explodedPositions,
                    "damagedPlayers", damagedPlayers
            );
            room.broadcast(objectMapper.writeValueAsString(data));

            // Check match winner
            checkMatchEnd(room);

        } catch (Exception e) {
            System.err.println("Lỗi gửi EXPLODE_BOMB: " + e.getMessage());
        }
    }

    private void checkPlayerHit(Room room, int x, int y, List<Map<String, Object>> damagedPlayers) {
        for (Player player : room.playerMap.values()) {
            if (player.isAlive && player.position.x == x && player.position.y == y) {
                player.health--;
                if (player.health <= 0) {
                    player.isAlive = false;
                }
                damagedPlayers.add(Map.of(
                        "playerId", player.id,
                        "health", player.health,
                        "isAlive", player.isAlive
                ));
            }
        }
    }

    private void checkMatchEnd(Room room) {
        if (room.members.size() <= 1) return; // Solo mode

        List<Player> alive = new ArrayList<>();
        for (Player p : room.playerMap.values()) {
            if (p.isAlive) alive.add(p);
        }

        if (alive.size() <= 1) {
            Player winner = alive.isEmpty() ? null : alive.get(0);
            room.status = RoomStatus.FINISHED;

            try {
                String winnerId = winner != null ? winner.id : "";
                String winnerName = winner != null ? winner.name : "Hòa";

                // Save to Neon DB
                MatchHistoryEntity match = new MatchHistoryEntity(
                        room.roomId, room.roomName, winnerId, winnerName, room.members.size()
                );
                matchHistoryRepository.save(match);

                // Update users in Neon DB
                for (Map.Entry<String, UserEntity> entry : sessionUsers.entrySet()) {
                    if (entry.getKey().equals(winnerId)) {
                        UserEntity u = entry.getValue();
                        u.setTotalWins(u.getTotalWins() + 1);
                        u.setScore(u.getScore() + 100);
                        u.setTotalMatches(u.getTotalMatches() + 1);
                        userRepository.save(u);
                    } else if (room.members.containsKey(entry.getKey())) {
                        UserEntity u = entry.getValue();
                        u.setTotalMatches(u.getTotalMatches() + 1);
                        userRepository.save(u);
                    }
                }

                Map<String, Object> endMsg = Map.of(
                        "action", "MATCH_RESULT",
                        "winnerId", winnerId,
                        "winnerName", winnerName
                );
                room.broadcast(objectMapper.writeValueAsString(endMsg));
            } catch (Exception e) {
                System.err.println("Lỗi lưu match result vào DB: " + e.getMessage());
            }
        }
    }

    private void broadcastRoomUpdate(Room room) {
        try {
            Map<String, Object> update = Map.of(
                    "action", "ROOM_UPDATE",
                    "roomId", room.roomId,
                    "roomName", room.roomName,
                    "members", room.getMembersList()
            );
            room.broadcast(objectMapper.writeValueAsString(update));
        } catch (Exception e) {
            System.err.println("Lỗi broadcast room update: " + e.getMessage());
        }
    }

    private void sendRoomList(WebSocketSession session) throws IOException {
        if (!session.isOpen()) return;
        List<Map<String, Object>> roomList = getRoomListSummary();
        Map<String, Object> data = Map.of(
                "action", "ROOM_LIST",
                "rooms", roomList
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
    }

    private void broadcastRoomList() {
        try {
            List<Map<String, Object>> roomList = getRoomListSummary();
            Map<String, Object> data = Map.of(
                    "action", "ROOM_LIST",
                    "rooms", roomList
            );
            String json = objectMapper.writeValueAsString(data);
            TextMessage textMessage = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    String rId = sessionToRoomId.get(session.getId());
                    if (rId == null) {
                        try {
                            session.sendMessage(textMessage);
                        } catch (IOException ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi broadcast room list: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> getRoomListSummary() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Room r : rooms.values()) {
            list.add(Map.of(
                    "roomId", r.roomId,
                    "roomName", r.roomName,
                    "currentPlayers", r.members.size(),
                    "maxPlayers", r.maxPlayers,
                    "status", r.status.name()
            ));
        }
        return list;
    }

    private void sendError(WebSocketSession session, String message) {
        if (!session.isOpen()) return;
        try {
            Map<String, Object> err = Map.of(
                    "action", "ERROR",
                    "message", message
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(err)));
        } catch (IOException e) {
            System.err.println("Lỗi gửi error message: " + e.getMessage());
        }
    }

    private String generateUniqueRoomId() {
        Random random = new Random();
        for (int i = 0; i < 100; i++) {
            String id = String.valueOf(random.nextInt(9000) + 1000);
            if (!rooms.containsKey(id)) {
                return id;
            }
        }
        return UUID.randomUUID().toString().substring(0, 6);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session);
        sessionUsers.remove(session.getId());
        handleLeaveRoom(session);
        System.out.println("==== Người chơi ID " + session.getId() + " đã ngắt kết nối ====");
    }

    public static class ClientMessage {
        public String action;
        public String token;
        public String roomId;
        public String roomName;
        public int maxPlayers = 4;
        public String playerName;
        public String direction;
        public String playerId;
    }
}
