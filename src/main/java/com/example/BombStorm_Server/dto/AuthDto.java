package com.example.BombStorm_Server.dto;

public class AuthDto {

    public static class RegisterRequest {
        private String username;
        private String password;
        private String displayName;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class AuthResponse {
        private boolean success;
        private String message;
        private String token;
        private Long userId;
        private String username;
        private String displayName;
        private int score;
        private int totalWins;
        private int totalMatches;

        public AuthResponse() {}

        public AuthResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }

        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }

        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }

        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }

        public int getTotalWins() { return totalWins; }
        public void setTotalWins(int totalWins) { this.totalWins = totalWins; }

        public int getTotalMatches() { return totalMatches; }
        public void setTotalMatches(int totalMatches) { this.totalMatches = totalMatches; }
    }

    public static class LeaderboardItemDto {
        private int rank;
        private String username;
        private String displayName;
        private int score;
        private int totalWins;
        private int totalMatches;

        public LeaderboardItemDto() {}

        public LeaderboardItemDto(int rank, String username, String displayName, int score, int totalWins, int totalMatches) {
            this.rank = rank;
            this.username = username;
            this.displayName = displayName;
            this.score = score;
            this.totalWins = totalWins;
            this.totalMatches = totalMatches;
        }

        public int getRank() { return rank; }
        public void setRank(int rank) { this.rank = rank; }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }

        public int getScore() { return score; }
        public void setScore(int score) { this.score = score; }

        public int getTotalWins() { return totalWins; }
        public void setTotalWins(int totalWins) { this.totalWins = totalWins; }

        public int getTotalMatches() { return totalMatches; }
        public void setTotalMatches(int totalMatches) { this.totalMatches = totalMatches; }
    }
}
