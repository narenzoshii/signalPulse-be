package com.signalpulse.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(
    name = "rss_feed",
    indexes = {
        @Index(name = "idx_rssfeed_enabled", columnList = "enabled"),
        @Index(name = "idx_rssfeed_category", columnList = "category_id")
    }
)
public class RssFeed {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 2048, unique = true)
    private String url;

    @Column(length = 500)
    private String description;

    private double trust = 1.0;
    private boolean enabled = true;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;
}
