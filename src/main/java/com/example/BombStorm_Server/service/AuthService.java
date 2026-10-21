package com.example.BombStorm_Server.service;

import com.example.BombStorm_Server.dto.AuthDto;
import com.example.BombStorm_Server.entity.UserEntity;
import com.example.BombStorm_Server.repository.UserRepository;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AuthService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    public AuthDto.AuthResponse register(AuthDto.RegisterRequest req) {
        if (req.getUsername() == null || req.getUsername().trim().isEmpty()) {
            return new AuthDto.AuthResponse(false, "Tên đăng nhập không được để trống!");
        }
        if (req.getPassword() == null || req.getPassword().length() < 4) {
            return new AuthDto.AuthResponse(false, "Mật khẩu phải có ít nhất 4 ký tự!");
        }

        String username = req.getUsername().trim().toLowerCase();
        if (userRepository.existsByUsername(username)) {
            return new AuthDto.AuthResponse(false, "Tên đăng nhập đã tồn tại, vui lòng chọn tên khác!");
        }

        String displayName = (req.getDisplayName() != null && !req.getDisplayName().trim().isEmpty())
                ? req.getDisplayName().trim()
                : req.getUsername().trim();

        // Hash password with BCrypt
        String hashedPassword = BCrypt.hashpw(req.getPassword(), BCrypt.gensalt(10));

        UserEntity user = new UserEntity(username, hashedPassword, displayName);
        user = userRepository.save(user);

        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getDisplayName());

        AuthDto.AuthResponse response = new AuthDto.AuthResponse(true, "Đăng ký tài khoản thành công!");
        populateUserData(response, user, token);
        return response;
    }

    public AuthDto.AuthResponse login(AuthDto.LoginRequest req) {
        if (req.getUsername() == null || req.getPassword() == null) {
            return new AuthDto.AuthResponse(false, "Vui lòng nhập tên đăng nhập và mật khẩu!");
        }

        String username = req.getUsername().trim().toLowerCase();
        Optional<UserEntity> userOpt = userRepository.findByUsername(username);

        if (userOpt.isEmpty()) {
            return new AuthDto.AuthResponse(false, "Tài khoản không tồn tại!");
        }

        UserEntity user = userOpt.get();
        boolean validPassword = BCrypt.checkpw(req.getPassword(), user.getPassword());
        if (!validPassword) {
            return new AuthDto.AuthResponse(false, "Mật khẩu không chính xác!");
        }

        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getDisplayName());

        AuthDto.AuthResponse response = new AuthDto.AuthResponse(true, "Đăng nhập thành công!");
        populateUserData(response, user, token);
        return response;
    }

    public AuthDto.AuthResponse getProfile(String token) {
        if (!jwtService.isValid(token)) {
            return new AuthDto.AuthResponse(false, "Token không hợp lệ hoặc đã hết hạn!");
        }

        Long userId = jwtService.getUserIdFromToken(token);
        if (userId == null) {
            return new AuthDto.AuthResponse(false, "Không tìm thấy người dùng!");
        }

        Optional<UserEntity> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return new AuthDto.AuthResponse(false, "Người dùng không tồn tại!");
        }

        UserEntity user = userOpt.get();
        AuthDto.AuthResponse response = new AuthDto.AuthResponse(true, "Lấy thông tin thành công!");
        populateUserData(response, user, token);
        return response;
    }

    public List<AuthDto.LeaderboardItemDto> getLeaderboard() {
        List<UserEntity> topUsers = userRepository.findTop10ByOrderByScoreDesc();
        List<AuthDto.LeaderboardItemDto> result = new ArrayList<>();
        int rank = 1;
        for (UserEntity u : topUsers) {
            result.add(new AuthDto.LeaderboardItemDto(
                    rank++,
                    u.getUsername(),
                    u.getDisplayName(),
                    u.getScore(),
                    u.getTotalWins(),
                    u.getTotalMatches()
            ));
        }
        return result;
    }

    private void populateUserData(AuthDto.AuthResponse res, UserEntity user, String token) {
        res.setToken(token);
        res.setUserId(user.getId());
        res.setUsername(user.getUsername());
        res.setDisplayName(user.getDisplayName());
        res.setScore(user.getScore());
        res.setTotalWins(user.getTotalWins());
        res.setTotalMatches(user.getTotalMatches());
    }
}
