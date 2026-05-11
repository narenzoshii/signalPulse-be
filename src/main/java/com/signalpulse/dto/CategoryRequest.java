package com.signalpulse.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class CategoryRequest {
    private Long id;

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 250)
    private String description;

    @NotNull
    @DecimalMin("0.0") @DecimalMax("10.0")
    private Double weight = 1.0;
}
