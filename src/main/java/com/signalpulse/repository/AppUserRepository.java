package com.signalpulse.repository;

import com.signalpulse.entity.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    @EntityGraph(attributePaths = {"roles", "roles.privileges"})
    Optional<AppUser> findByUsername(String username);

    @Override
    @EntityGraph(attributePaths = {"roles", "roles.privileges"})
    List<AppUser> findAll();

    @Override
    @EntityGraph(attributePaths = {"roles", "roles.privileges"})
    Page<AppUser> findAll(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"roles", "roles.privileges"})
    Optional<AppUser> findById(Long id);
}
