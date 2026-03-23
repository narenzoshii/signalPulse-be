package com.signalpulse.repository;

import com.signalpulse.entity.ScanResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ScanResultRepository extends JpaRepository<ScanResult, Long> {
    List<ScanResult> findTop5ByOrderByTimestampDesc();
}
