package com.signalpulse.repository;

import com.signalpulse.entity.ScannedArticle;
import com.signalpulse.entity.ScanResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ScannedArticleRepository extends JpaRepository<ScannedArticle, Long> {
    List<ScannedArticle> findByScanResult(ScanResult scanResult);
    List<ScannedArticle> findTop50ByOrderByScanResultTimestampDesc();
    boolean existsByLink(String link);

    @Query("SELECT sa.link FROM ScannedArticle sa WHERE sa.scanResult.timestamp >= :since")
    List<String> findLinksSince(@Param("since") LocalDateTime since);

    @Query("SELECT sa.link FROM ScannedArticle sa")
    List<String> findAllLinks();
}
