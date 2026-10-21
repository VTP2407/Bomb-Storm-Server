package com.example.BombStorm_Server.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class JwtService {

    private static final String SECRET = "BombStorm_SuperSecretKey_2026_JWT_Auth_Token_Security";
    private static final long EXPIRATION_TIME = 7L * 24 * 60 * 60 * 1000; // 7 days

    private final Algorithm algorithm = Algorithm.HMAC256(SECRET);

    public String generateToken(Long userId, String username, String displayName) {
        return JWT.create()
                .withSubject(username)
                .withClaim("userId", userId)
                .withClaim("displayName", displayName != null ? displayName : username)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .sign(algorithm);
    }

    public DecodedJWT verifyToken(String token) {
        if (token == null || token.trim().isEmpty()) return null;
        try {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            JWTVerifier verifier = JWT.require(algorithm).build();
            return verifier.verify(token);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isValid(String token) {
        return verifyToken(token) != null;
    }

    public String getUsernameFromToken(String token) {
        DecodedJWT jwt = verifyToken(token);
        return jwt != null ? jwt.getSubject() : null;
    }

    public Long getUserIdFromToken(String token) {
        DecodedJWT jwt = verifyToken(token);
        return jwt != null ? jwt.getClaim("userId").asLong() : null;
    }

    public String getDisplayNameFromToken(String token) {
        DecodedJWT jwt = verifyToken(token);
        return jwt != null ? jwt.getClaim("displayName").asString() : null;
    }
}
