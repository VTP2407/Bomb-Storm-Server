package com.example.BombStorm_Server.repository;

import com.example.BombStorm_Server.entity.MatchHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MatchHistoryRepository extends JpaRepository<MatchHistoryEntity, Long> {
    List<MatchHistoryEntity> findTop20ByOrderByPlayedAtDesc();
}
