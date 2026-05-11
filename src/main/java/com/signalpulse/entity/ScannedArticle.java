package com.signalpulse.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(
    name = "scanned_article",
    indexes = {
        @Index(name = "idx_article_link", columnList = "link"),
        @Index(name = "idx_article_scan_result", columnList = "scan_result_id")
    }
)
public class ScannedArticle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 500)
    private String title;

    @Column(unique = true, nullable = false, length = 1024)
    private String link;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 200)
    private String source;

    private LocalDateTime publishedAt;
    private Double score;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "scan_result_id")
    private ScanResult scanResult;
}
