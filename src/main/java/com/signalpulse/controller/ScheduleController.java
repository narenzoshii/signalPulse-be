package com.signalpulse.controller;

import com.signalpulse.entity.ScheduleConfig;
import com.signalpulse.service.SchedulerService;
import com.signalpulse.repository.ScheduleConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ScheduleController {
    private final SchedulerService schedulerService;
    private final ScheduleConfigRepository scheduleConfigRepository;
    
    @GetMapping("/configs")
    public List<ScheduleConfig> getConfigs() {
        return scheduleConfigRepository.findAll();
    }
    
    @PostMapping("/configs")
    public ScheduleConfig saveConfig(@RequestBody ScheduleConfig config) {
        ScheduleConfig saved = scheduleConfigRepository.save(config);
        schedulerService.updateSchedules();
        return saved;
    }

    @PostMapping("/config/cron_schedule")
    public void updateCronSchedule(@RequestBody java.util.Map<String, String> payload) {
        String cron = payload.get("cronExpression");
        // For "multiple schedules", we can either replace or append. 
        // Given the UI shows one input, it might be the primary one, 
        // but the user said "multiple". I'll add a new one each time 
        // or update the default one. I'll add a new one for now.
        ScheduleConfig config = new ScheduleConfig();
        config.setName("Dynamic Schedule " + System.currentTimeMillis());
//        config.setCronExpression(cron);
        config.setActive(true);
        scheduleConfigRepository.save(config);
        schedulerService.updateSchedules();
    }
    
    @PostMapping("/trigger-sync")
    public void sync() {
        schedulerService.updateSchedules();
    }

    @DeleteMapping("/configs/{id}")
    public void deleteConfig(@PathVariable Long id) {
        scheduleConfigRepository.deleteById(id);
        schedulerService.updateSchedules();
    }
}
