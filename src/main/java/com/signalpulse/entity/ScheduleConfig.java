package com.signalpulse.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.List;

@Entity
@Data
public class ScheduleConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String name;
    
//    private String cronExpression;
    
    private boolean active = true;
    
    @ElementCollection
    private List<String> dailyTimes;
    
    @ElementCollection
    private List<String> weeklyDays; // MONDAY, TUESDAY...
    
    private boolean weeklyDigestEnabled = false;
    
//    private String description;
}
