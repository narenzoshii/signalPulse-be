package com.signalpulse.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.List;

@Entity
@Data
public class TopicRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(unique = true)
    private String topicKey;
    
    private double weight;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "topic_rule_patterns", joinColumns = @JoinColumn(name = "topic_rule_id"))
    @Column(name = "patterns", length = 1000)
    private List<String> patterns; // regex patterns
    
    private boolean active = true;
}
