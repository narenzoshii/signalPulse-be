package com.signalpulse.controller;

import com.signalpulse.entity.Role;
import com.signalpulse.entity.Privilege;
import com.signalpulse.repository.RoleRepository;
import com.signalpulse.repository.PrivilegeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@PreAuthorize("hasAuthority('OP_MANAGE_USERS')")
public class RoleController {
    private final RoleRepository roleRepository;
    private final PrivilegeRepository privilegeRepository;

    @GetMapping
    public List<Role> getAll() {
        return roleRepository.findAll();
    }

    @PostMapping
    public Role save(@RequestBody Role role) {
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
