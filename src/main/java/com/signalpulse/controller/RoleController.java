package com.signalpulse.controller;

import com.signalpulse.dto.PageResponse;
import com.signalpulse.dto.RoleRequest;
import com.signalpulse.entity.Privilege;
import com.signalpulse.entity.Role;
import com.signalpulse.repository.PrivilegeRepository;
import com.signalpulse.repository.RoleRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('OP_MANAGE_USERS')")
public class RoleController {
    private static final int MAX_PAGE_SIZE = 200;

    private final RoleRepository roleRepository;
    private final PrivilegeRepository privilegeRepository;

    @GetMapping
    public PageResponse<Role> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, MAX_PAGE_SIZE), Sort.by("name").ascending());
        return PageResponse.from(roleRepository.findAll(pageable));
    }

    @PostMapping
    public Role save(@Valid @RequestBody RoleRequest body) {
        Role role = body.getId() == null
                ? new Role()
                : roleRepository.findById(body.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Role not found: " + body.getId()));
        role.setName(body.getName());
        if (body.getPrivilegeIds() != null && !body.getPrivilegeIds().isEmpty()) {
            List<Privilege> privileges = privilegeRepository.findAllById(body.getPrivilegeIds());
            role.setPrivileges(new HashSet<>(privileges));
        } else {
            role.setPrivileges(new HashSet<>());
        }
        return roleRepository.save(role);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        roleRepository.deleteById(id);
    }

    @GetMapping("/privileges")
    public List<Privilege> getAllPrivileges() {
        return privilegeRepository.findAll();
    }
}
