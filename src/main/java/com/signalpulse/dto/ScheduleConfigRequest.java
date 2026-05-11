package com.signalpulse.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class ScheduleConfigRequest {
    private Long id;

    @NotBlank
    @Size(max = 100)
    private String name;

    private boolean active = true;

    @NotEmpty(message = "At least one time required")
    private List<@NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "time must be HH:mm") String> dailyTimes = new ArrayList<>();

    private List<@Pattern(regexp = "MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY") String> weeklyDays = new ArrayList<>();

    private boolean weeklyDigestEnabled = false;
}
