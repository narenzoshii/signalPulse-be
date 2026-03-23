package com.signalpulse;

import com.signalpulse.entity.AppUser;
import com.signalpulse.repository.AppUserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SignalPulseApplication {
    public static void main(String[] args) {
        SpringApplication.run(SignalPulseApplication.class, args);
    }

}
