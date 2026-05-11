package com.signalpulse.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.ArrayList;
import java.util.List;

@Data
public class TopicRuleRequest {
    private Long id;

    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "topicKey must be alphanumeric, underscore or dash")
    private String topicKey;

    @Size(max = 250)
    private String description;

    @DecimalMin("0.0") @DecimalMax("100.0")
    private double weight = 1.0;

    @NotEmpty(message = "At least one pattern is required")
    @Size(max = 50, message = "Too many patterns (max 50)")
    private List<@NotBlank @Size(max = 1000) String> patterns = new ArrayList<>();

    private boolean active = true;
}
