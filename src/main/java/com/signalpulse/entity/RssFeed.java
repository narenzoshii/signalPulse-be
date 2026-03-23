package com.signalpulse.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class RssFeed {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private String url;
    private double trust = 0.9;
    private boolean enabled = true;
    
    @ManyToOne
    private Category category;
}
