package com.example.BombStorm_Server.model;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Room {
    public String roomId;
    public String roomName;
    public int maxPlayers;
    public String hostId;
    public RoomStatus status = RoomStatus.LOBBY;

    public final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    public final Map<String, RoomMember> members = new ConcurrentHashMap<>();

    public final Map<String, Bomb> bombMap = new ConcurrentHashMap<>();
    public final Map<String, Player> playerMap = new ConcurrentHashMap<>();
    public volatile GameMap currentMap = null;

    public Room() {}

    public Room(String roomId, String roomName, int maxPlayers, String hostId) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.maxPlayers = maxPlayers;
        this.hostId = hostId;
    }

    public synchronized int getNextAvailableSlot() {
        boolean[] occupied = new boolean[maxPlayers];
        for (RoomMember member : members.values()) {
            if (member.slotIndex >= 0 && member.slotIndex < maxPlayers) {
                occupied[member.slotIndex] = true;
            }
        }
        for (int i = 0; i < maxPlayers; i++) {
            if (!occupied[i]) return i;
        }
        return -1;
    }

    public synchronized boolean addMember(WebSocketSession session, String playerName) {
        if (members.size() >= maxPlayers || status != RoomStatus.LOBBY) {
            return false;
        }
        int slot = getNextAvailableSlot();
        if (slot == -1) return false;

        boolean isHost = members.isEmpty() || session.getId().equals(hostId);
        if (isHost) {
            hostId = session.getId();
        }
        RoomMember member = new RoomMember(session.getId(), playerName, slot, isHost, isHost);
        members.put(session.getId(), member);
        sessions.put(session.getId(), session);
        return true;
    }

    public synchronized boolean removeMember(String playerId) {
        sessions.remove(playerId);
        RoomMember removed = members.remove(playerId);
        playerMap.remove(playerId);

        if (removed != null && removed.isHost && !members.isEmpty()) {
            RoomMember newHost = members.values().iterator().next();
            newHost.isHost = true;
            newHost.isReady = true;
            this.hostId = newHost.playerId;
        }
        return members.isEmpty();
    }

    public boolean isAllReady() {
        if (members.isEmpty()) return false;
        for (RoomMember m : members.values()) {
            if (!m.isReady) return false;
        }
        return true;
    }

    public void broadcast(String message) {
        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessions.values()) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    System.err.println("Broadcast error in room " + roomId + ": " + e.getMessage());
                }
            }
        }
    }

    public List<RoomMember> getMembersList() {
        List<RoomMember> list = new ArrayList<>(members.values());
        list.sort(Comparator.comparingInt(m -> m.slotIndex));
        return list;
    }
}
