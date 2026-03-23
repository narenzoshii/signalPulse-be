package com.signalpulse.controller;

import com.signalpulse.entity.AppUser;
import com.signalpulse.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class AuthController {
    
    private final AppUserRepository appUserRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> creds) {
        String username = creds.get("username");
        String password = creds.get("password");
        
        Optional<AppUser> userOpt = appUserRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            if (passwordEncoder.matches(password, user.getPassword())) {
                java.util.Set<String> authorities = new java.util.HashSet<>();
                user.getRoles().forEach(r -> {
                    authorities.add("ROLE_" + r.getName());
                    r.getPrivileges().forEach(p -> authorities.add(p.getName()));
                });
                return ResponseEntity.ok(Map.of(
                    "token", "dummy-auth-token-123", 
                    "user", user,
                    "authorities", authorities
                ));
            }
        }
        
        return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
    }
}
