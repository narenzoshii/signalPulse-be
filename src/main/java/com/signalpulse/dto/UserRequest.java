package com.signalpulse.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.HashSet;
import java.util.Set;

@Data
public class UserRequest {
    private Long id;

    @NotBlank
    @Size(min = 3, max = 100)
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "username may contain letters, digits, dot, underscore or dash")
    private String username;

    // Required on create; optional on update (blank means "keep current")
    @Size(min = 8, max = 200, message = "password must be 8–200 chars")
    private String password;

    private Set<@NotNull Long> roleIds = new HashSet<>();
}
