package com.signalpulse.controller;

import com.signalpulse.entity.AppUser;
import com.signalpulse.entity.Role;
import com.signalpulse.repository.AppUserRepository;
import com.signalpulse.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasAuthority('OP_MANAGE_USERS')")
public class UserController {
    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public List<AppUser> getAll() {
        return userRepository.findAll();
    }

    @PostMapping
    public AppUser save(@RequestBody AppUser user) {
        if (user.getId() == null) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            AppUser existing = userRepository.findById(user.getId()).orElse(null);
            if (existing != null && !user.getPassword().startsWith("$2a$")) {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            } else if (existing != null) {
                user.setPassword(existing.getPassword());
            }
        }
        return userRepository.save(user);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        userRepository.deleteById(id);
    }

    @GetMapping("/roles")
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }
}
