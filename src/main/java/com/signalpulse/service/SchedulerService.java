package com.signalpulse.service;

import com.signalpulse.entity.ScheduleConfig;
import com.signalpulse.repository.ScheduleConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchedulerService implements SchedulingConfigurer {
    private final ScheduleConfigRepository scheduleConfigRepository;
    private final TaskScheduler taskScheduler;
    private final ScannerService scannerService;
    
    private final Map<Long, List<ScheduledFuture<?>>> scheduledTasks = new HashMap<>();

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        // This is primarily for fixed @Scheduled tasks. 
    }

    public void updateSchedules() {
        // Cancel all existing tasks
        scheduledTasks.values().forEach(futures -> futures.forEach(f -> f.cancel(false)));
        scheduledTasks.clear();

        List<ScheduleConfig> configs = scheduleConfigRepository.findByActiveTrue();
        for (ScheduleConfig config : configs) {
            java.util.ArrayList<ScheduledFuture<?>> futures = new java.util.ArrayList<>();
            
//            // 1. Specific Cron Expression (if provided)
//            if (config.getCronExpression() != null && !config.getCronExpression().isEmpty()) {
//                scheduleTask(config.getCronExpression(), config.getName(), futures);
//            }
            
            // 2. Dynamic Daily/Weekly Times
            if (config.getDailyTimes() != null && !config.getDailyTimes().isEmpty()) {
                String days = (config.getWeeklyDays() != null && !config.getWeeklyDays().isEmpty()) 
                                ? String.join(",", config.getWeeklyDays())
                                : "*"; // default to daily
                // weeklyDays might be [MONDAY, TUESDAY] but CRON likes MON,TUE
                if (!days.equals("*")) {
                    days = days.replace("MONDAY", "MON")
                               .replace("TUESDAY", "TUE")
                               .replace("WEDNESDAY", "WED")
                               .replace("THURSDAY", "THU")
                               .replace("FRIDAY", "FRI")
                               .replace("SATURDAY", "SAT")
                               .replace("SUNDAY", "SUN");
                }

                for (String timeStr : config.getDailyTimes()) {
                    try {
                        String[] parts = timeStr.split(":");
                        int hour = Integer.parseInt(parts[0]);
                        int minute = Integer.parseInt(parts[1]);
                        String cron = String.format("0 %d %d * * %s", minute, hour, days);
                        scheduleTask(cron, config.getName(), futures);
                    } catch (Exception e) {
                        log.error("Failed to parse time '{}' for schedule '{}'.", timeStr, config.getName());
                    }
                }
            }
            
            if (!futures.isEmpty()) {
                scheduledTasks.put(config.getId(), futures);
            }
        }
    }

    public void pauseAll() {
        scheduledTasks.values().forEach(futures -> futures.forEach(f -> f.cancel(false)));
        scheduledTasks.clear();
        log.info("Paused all scheduled tasks.");
    }

    private void scheduleTask(String cron, String name, List<ScheduledFuture<?>> futures) {
        log.info("Scheduling task '{}' with cron: {}", name, cron);
        ScheduledFuture<?> future = taskScheduler.schedule(
            scannerService::runScan, 
            new CronTrigger(cron)
        );
        futures.add(future);
    }
}
