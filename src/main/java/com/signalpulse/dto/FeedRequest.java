package com.signalpulse.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class FeedRequest {
    private Long id;

    @NotBlank
    @Size(max = 200)
    private String name;

    @NotBlank
    @Size(max = 2048)
    @Pattern(regexp = "^https?://.+", message = "URL must start with http:// or https://")
    private String url;

    @Size(max = 500)
    private String description;

    @DecimalMin("0.0") @DecimalMax("10.0")
    private double trust = 1.0;

    private boolean enabled = true;

    private Long categoryId;
}
