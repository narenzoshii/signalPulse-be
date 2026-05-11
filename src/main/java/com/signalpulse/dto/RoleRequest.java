package com.signalpulse.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.HashSet;
import java.util.Set;

@Data
public class RoleRequest {
    private Long id;

    @NotBlank
    @Size(min = 2, max = 50)
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "Role name must be uppercase letters, digits or underscore")
    private String name;

    private Set<@NotNull Long> privilegeIds = new HashSet<>();
}
