package com.signalpulse.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class HtmlPage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    private String url;
    private String type = "html_list"; // html_list | html_detail
    
    private String listSelector;
    private String titleSelector;
    private String linkSelector;
    private String dateSelector; // CSS selector or date parse pattern (String/varchar)
    
    private double trust = 0.9;
    private boolean enabled = true;

    @ManyToOne
    private Category category;
}
