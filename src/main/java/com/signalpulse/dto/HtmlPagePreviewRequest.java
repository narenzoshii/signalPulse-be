package com.signalpulse.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class HtmlPagePreviewRequest {

    @NotBlank
    @Size(max = 2048)
    @Pattern(regexp = "^https?://.+", message = "URL must start with http:// or https://")
    private String url;

    @Pattern(regexp = "auto|manual", message = "discoveryMode must be 'auto' or 'manual'")
    private String discoveryMode = "auto";

    @Size(max = 500)
    private String listSelector;
    @Size(max = 500)
    private String titleSelector;
    @Size(max = 500)
    private String linkSelector;
}
