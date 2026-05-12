package com.signalpulse.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(
    name = "html_page",
    indexes = {
        @Index(name = "idx_htmlpage_enabled", columnList = "enabled"),
        @Index(name = "idx_htmlpage_category", columnList = "category_id")
    }
)
public class HtmlPage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 2048, unique = true)
    private String url;

    @Column(length = 500)
    private String description;

    @Column(length = 32)
    private String type = "html_list"; // html_list | html_detail

    /**
     * "auto" → HtmlAutoDiscovery infers article links from the DOM at scan time;
     * the user only needs a URL. "manual" → use the CSS selectors below.
     */
    @Column(length = 16, nullable = false)
    private String discoveryMode = "auto";

    @Column(length = 500)
    private String listSelector;
    @Column(length = 500)
    private String titleSelector;
    @Column(length = 500)
    private String linkSelector;
    @Column(length = 500)
    private String dateSelector;

    private double trust = 1.0;
    private boolean enabled = true;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "category_id")
    private Category category;

    // ---- Health tracking ----
    private LocalDateTime lastScanAt;
    @Column(length = 16)
    private String lastScanStatus;   // "SUCCESS" | "FAILURE"
    @Column(length = 500)
    private String lastScanError;
    private int consecutiveFailures = 0;
    private int lastArticleCount = 0;
}
