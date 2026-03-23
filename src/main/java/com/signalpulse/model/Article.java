package com.signalpulse.model;

import lombok.Data;
import java.util.Date;

@Data
public class Article {
    private String title;
    private String link;
    private String description;
    private String source;
    private Date publishedDate;
    private double baseScore;
    private double score;
}
