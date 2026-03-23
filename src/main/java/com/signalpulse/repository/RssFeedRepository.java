package com.signalpulse.repository;

import com.signalpulse.entity.RssFeed;
import org.springframework.data.jpa.repository.JpaRepository;

@org.springframework.stereotype.Repository
public interface RssFeedRepository extends JpaRepository<RssFeed, Long> {
    java.util.List<RssFeed> findByEnabledTrue();
}
