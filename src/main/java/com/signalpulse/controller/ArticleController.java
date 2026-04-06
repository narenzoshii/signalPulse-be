package com.signalpulse.controller;

import com.signalpulse.entity.ScanResult;
import com.signalpulse.repository.ScanResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/articles")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ArticleController {
    private final ScanResultRepository scanResultRepository;
    private final com.signalpulse.service.DashboardService dashboardService;
    
    @GetMapping
    public List<com.signalpulse.dto.ScannedArticleDTO> getArticles() {
        return dashboardService.getLatestResults();
    }

    @GetMapping("/history")
    public List<ScanResult> getHistory() {
        return scanResultRepository.findTop5ByOrderByTimestampDesc();
    }
}
