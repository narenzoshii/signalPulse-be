package com.signalpulse.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
public class ScheduleConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private boolean active = true;

    /**
     * EAGER: this entity is serialized to JSON and also iterated by
     * SchedulerService.updateSchedules() outside an open Hibernate session
     * (spring.jpa.open-in-view: false). Without eager load both paths blow up
     * with LazyInitializationException.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> dailyTimes = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> weeklyDays = new ArrayList<>();

    private boolean weeklyDigestEnabled = false;
}
