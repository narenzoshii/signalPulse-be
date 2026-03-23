package com.signalpulse.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
public class Privilege {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String name; // e.g. OP_READ, OP_TRIGGER, OP_WRITE_SOURCE

    public Privilege(String name) {
        this.name = name;
    }
}
