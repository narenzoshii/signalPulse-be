package com.signalpulse.repository;

import com.signalpulse.entity.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    @EntityGraph(attributePaths = {"privileges"})
    Optional<Role> findByName(String name);

    @Override
    @EntityGraph(attributePaths = {"privileges"})
    List<Role> findAll();

    @Override
    @EntityGraph(attributePaths = {"privileges"})
    Page<Role> findAll(Pageable pageable);
}
