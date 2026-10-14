package com.example.BombStorm_Server.service;

import com.example.BombStorm_Server.model.GameMap;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Service
public class MapGeneratorService {

    // Nơi lưu các file cấu hình Map đọc từ JSON
    private final Map<String, GameMap> mapConfigs = new HashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 1. CHẠY LÚC SERVER KHỞI ĐỘNG ĐỂ ĐỌC FILE JSON
    @PostConstruct
    public void initMapConfigs() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:maps/*.json");

            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    GameMap config = objectMapper.readValue(is, GameMap.class);
                    mapConfigs.put(config.getId(), config);
                    System.out.println("Đã load config map: " + config.getName());
                }
            }
        } catch (Exception e) {
            System.err.println("Lỗi khi load config maps: " + e.getMessage());
        }
    }

    // 2. HÀM TẠO MAP DỰA TRÊN ID TỪ JSON
    // Gọi hàm này mỗi khi Client kết nối để tạo 1 trận mới
    public GameMap generateMapSession(String mapId) {
        GameMap config = mapConfigs.get(mapId);

        if (config == null) {
            throw new RuntimeException("Không tìm thấy cấu hình map: " + mapId);
        }

        int width = config.getWidth();
        int height = config.getHeight();
        int boxAmount = config.getBoxAmount();
        int[] mapData = config.getMapData();

        if (mapData == null || mapData.length != width * height) {
            throw new IllegalArgumentException("Dữ liệu map không hợp lệ");
        }

        // Bước B: Rải hộp ngẫu nhiên
        Random random = new Random();
        int placedBoxes = 0;
        int attempts = width * height * 10;

        while (placedBoxes < boxAmount && attempts > 0) {
            int x = random.nextInt((width - 1) - 1) + 1;
            int y = random.nextInt((height - 1) - 1) + 1;
            int index = y * width + x;

            if (mapData[index] == 0) {
                mapData[index] = 2; // 2 = Hộp
                placedBoxes++;
            }
            attempts--;
        }

        // Bước C: Copy config cũ và gán data mới sinh ra
        GameMap newSessionMap = new GameMap();
        newSessionMap.setId(config.getId());
        newSessionMap.setName(config.getName());
        newSessionMap.setWidth(width);
        newSessionMap.setHeight(height);
        newSessionMap.setBoxAmount(boxAmount);
        newSessionMap.setMapData(mapData); // Gắn mảng đã random vào

        return newSessionMap;
    }
}
