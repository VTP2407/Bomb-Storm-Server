package com.example.BombStorm_Server.controller;

import com.example.BombStorm_Server.dto.AuthDto;
import com.example.BombStorm_Server.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthDto.AuthResponse> register(@RequestBody AuthDto.RegisterRequest req) {
        AuthDto.AuthResponse res = authService.register(req);
        if (!res.isSuccess()) {
            return ResponseEntity.badRequest().body(res);
        }
        return ResponseEntity.ok(res);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthDto.AuthResponse> login(@RequestBody AuthDto.LoginRequest req) {
        AuthDto.AuthResponse res = authService.login(req);
        if (!res.isSuccess()) {
            return ResponseEntity.badRequest().body(res);
        }
        return ResponseEntity.ok(res);
    }

    @GetMapping("/me")
    public ResponseEntity<AuthDto.AuthResponse> getProfile(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader == null || authHeader.trim().isEmpty()) {
            return ResponseEntity.status(401).body(new AuthDto.AuthResponse(false, "Thiếu Authentication Token!"));
        }
        AuthDto.AuthResponse res = authService.getProfile(authHeader);
        if (!res.isSuccess()) {
            return ResponseEntity.status(401).body(res);
        }
        return ResponseEntity.ok(res);
    }

    @GetMapping("/leaderboard")
    public ResponseEntity<List<AuthDto.LeaderboardItemDto>> getLeaderboard() {
        return ResponseEntity.ok(authService.getLeaderboard());
    }
}
