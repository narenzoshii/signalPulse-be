package com.signalpulse.repository;

import com.signalpulse.entity.ScheduleConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ScheduleConfigRepository extends JpaRepository<ScheduleConfig, Long> {
    List<ScheduleConfig> findByActiveTrue();
}
