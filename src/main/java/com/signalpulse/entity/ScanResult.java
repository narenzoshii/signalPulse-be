package com.signalpulse.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data
@Table(indexes = {
    @Index(name = "idx_scanresult_timestamp", columnList = "timestamp DESC")
})
public class ScanResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private LocalDateTime timestamp;
    private Integer articlesFound;
    private Long durationMs;
    private Integer newItemsCount;
    
    private String status; // SUCCESS, FAILURE
    private String triggerType; // MANUAL, SCHEDULED
}
