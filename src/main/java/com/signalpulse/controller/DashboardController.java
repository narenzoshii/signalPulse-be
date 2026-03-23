package com.signalpulse.controller;

import com.signalpulse.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DashboardController {
    private final DashboardService dashboardService;
    
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return dashboardService.getStats();
    }

    @GetMapping("/latest-results")
    public java.util.List<com.signalpulse.dto.ScannedArticleDTO> getLatestResults() {
        return dashboardService.getLatestResults();
    }
}
