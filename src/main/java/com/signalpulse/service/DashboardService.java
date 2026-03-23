package com.signalpulse.service;

import com.signalpulse.entity.ScanResult;
import com.signalpulse.entity.ScannedArticle;
import com.signalpulse.repository.ScanResultRepository;
import com.signalpulse.repository.ScannedArticleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final ScanResultRepository scanResultRepository;
    private final ScannedArticleRepository scannedArticleRepository;
    
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalScans", scanResultRepository.count());
        stats.put("recentResults", scanResultRepository.findTop5ByOrderByTimestampDesc());
        // Add more complex stats here (e.g., articles per day)
        
        return stats;
    }

    public List<ScannedArticle> getLatestResults() {
        return scannedArticleRepository.findTop50ByOrderByScanResultTimestampDesc();
    }
}
