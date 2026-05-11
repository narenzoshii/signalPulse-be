package com.signalpulse.controller;

import com.signalpulse.dto.PageResponse;
import com.signalpulse.dto.ScheduleConfigRequest;
import com.signalpulse.entity.ScheduleConfig;
import com.signalpulse.repository.ScheduleConfigRepository;
import com.signalpulse.service.SchedulerService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ScheduleController {
    private static final int MAX_PAGE_SIZE = 200;

    private final SchedulerService schedulerService;
    private final ScheduleConfigRepository scheduleConfigRepository;

    @GetMapping("/configs")
    @PreAuthorize("hasAuthority('OP_READ_ALL')")
    public PageResponse<ScheduleConfig> getConfigs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, MAX_PAGE_SIZE), Sort.by("name").ascending());
        return PageResponse.from(scheduleConfigRepository.findAll(pageable));
    }

    @PostMapping("/configs")
    @PreAuthorize("hasAuthority('OP_MANAGE_CONFIG')")
    public ScheduleConfig saveConfig(@Valid @RequestBody ScheduleConfigRequest body) {
        ScheduleConfig config = body.getId() == null
                ? new ScheduleConfig()
                : scheduleConfigRepository.findById(body.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Schedule not found: " + body.getId()));
        config.setName(body.getName());
        config.setActive(body.isActive());
        config.setDailyTimes(new ArrayList<>(body.getDailyTimes()));
        config.setWeeklyDays(new ArrayList<>(body.getWeeklyDays()));
        config.setWeeklyDigestEnabled(body.isWeeklyDigestEnabled());
        ScheduleConfig saved = scheduleConfigRepository.save(config);
        schedulerService.updateSchedules();
        return saved;
    }

    @PostMapping("/trigger-sync")
    @PreAuthorize("hasAuthority('OP_MANAGE_CONFIG')")
    public void sync() {
        schedulerService.updateSchedules();
    }

    @DeleteMapping("/configs/{id}")
    @PreAuthorize("hasAuthority('OP_MANAGE_CONFIG')")
    public void deleteConfig(@PathVariable Long id) {
        scheduleConfigRepository.deleteById(id);
        schedulerService.updateSchedules();
    }
}
