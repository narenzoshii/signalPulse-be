package com.signalpulse.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
public class ScannedArticle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    @Column(unique = true, length = 512)
    private String link;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    private String source;
    private LocalDateTime publishedAt;
    private Double score;

    @ManyToOne
    @JoinColumn(name = "scan_result_id")
    private com.signalpulse.entity.ScanResult scanResult;
}
