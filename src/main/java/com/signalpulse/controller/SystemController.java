package com.signalpulse.controller;

import com.signalpulse.service.SchedulerService;
import com.signalpulse.service.ScannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SystemController {
    private final SchedulerService schedulerService;
    private final ScannerService scannerService;

    @PostMapping("/pause-jobs")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('OP_PAUSE_JOBS')")
    public void pauseJobs() {
        // Implementation: Set a global flag or de-register all schedules
        schedulerService.pauseAll();
    }


    @PostMapping("/resume-jobs")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('OP_PAUSE_JOBS')")
    public void resumeJobs() {
        schedulerService.updateSchedules();
    }


    @PostMapping("/trigger-now")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('OP_TRIGGER_SCAN')")
    public void triggerNow() {
        scannerService.runScan("MANUAL");
    }

}
