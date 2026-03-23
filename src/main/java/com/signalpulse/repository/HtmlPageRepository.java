package com.signalpulse.repository;

import com.signalpulse.entity.HtmlPage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HtmlPageRepository extends JpaRepository<HtmlPage, Long> {
    List<HtmlPage> findByEnabledTrue();
}
