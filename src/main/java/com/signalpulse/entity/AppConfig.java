package com.signalpulse.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
public class AppConfig {
    @Id
    private String configKey;
    private String configValue;
}
