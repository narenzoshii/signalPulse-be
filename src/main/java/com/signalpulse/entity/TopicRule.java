package com.signalpulse.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@Table(
    name = "topic_rule",
    indexes = {
        @Index(name = "idx_topicrule_active", columnList = "active")
    }
)
public class TopicRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String topicKey;

    @Column(length = 250)
    private String description;

    @Column(nullable = false)
    private double weight = 1.0;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "topic_rule_patterns", joinColumns = @JoinColumn(name = "topic_rule_id"))
    @Column(name = "patterns", length = 1000)
    private List<String> patterns = new ArrayList<>();

    @Column(nullable = false)
    private boolean active = true;
}
