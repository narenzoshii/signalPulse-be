package com.signalpulse.repository;

import com.signalpulse.entity.ScannedArticle;
import com.signalpulse.entity.ScanResult;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ScannedArticleRepository extends JpaRepository<ScannedArticle, Long> {
    List<ScannedArticle> findByScanResult(ScanResult scanResult);
    List<ScannedArticle> findTop50ByOrderByScanResultTimestampDesc();
    boolean existsByLink(String link);
}
