package com.signalpulse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScannedArticleDTO {
    private Long id;
    private String title;
    private String link;
    private String source;
    private LocalDateTime publishedAt;
    private Double score;
}
