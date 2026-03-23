package com.signalpulse.repository;

import com.signalpulse.entity.Privilege;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PrivilegeRepository extends JpaRepository<Privilege, Long> {
    Optional<Privilege> findByName(String name);
}
