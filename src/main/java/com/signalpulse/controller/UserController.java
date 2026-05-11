package com.signalpulse.controller;

import com.signalpulse.dto.PageResponse;
import com.signalpulse.dto.UserRequest;
import com.signalpulse.entity.AppUser;
import com.signalpulse.entity.Role;
import com.signalpulse.repository.AppUserRepository;
import com.signalpulse.repository.RoleRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('OP_MANAGE_USERS')")
public class UserController {
    private static final int MAX_PAGE_SIZE = 200;

    private final AppUserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public PageResponse<AppUser> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, MAX_PAGE_SIZE), Sort.by("username").ascending());
        return PageResponse.from(userRepository.findAll(pageable));
    }

    @PostMapping
    public AppUser save(@Valid @RequestBody UserRequest body) {
        AppUser user;
        if (body.getId() == null) {
            // Create
            if (body.getPassword() == null || body.getPassword().isBlank()) {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Password is required when creating a user");
            }
            user = new AppUser();
            user.setUsername(body.getUsername());
            user.setPassword(passwordEncoder.encode(body.getPassword()));
        } else {
            // Update
            user = userRepository.findById(body.getId())
                    .orElseThrow(() -> new EntityNotFoundException("User not found: " + body.getId()));
            user.setUsername(body.getUsername());
            if (body.getPassword() != null && !body.getPassword().isBlank()) {
                user.setPassword(passwordEncoder.encode(body.getPassword()));
            }
        }

        if (body.getRoleIds() != null && !body.getRoleIds().isEmpty()) {
            List<Role> roles = roleRepository.findAllById(body.getRoleIds());
            user.setRoles(new HashSet<>(roles));
        } else {
            user.setRoles(new HashSet<>());
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
