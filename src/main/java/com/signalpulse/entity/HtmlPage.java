package com.signalpulse.entity;

import jakarta.persistence.*;
import lombok.Data;

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
}
