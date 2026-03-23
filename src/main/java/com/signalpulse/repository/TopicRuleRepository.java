package com.signalpulse.repository;

import com.signalpulse.entity.TopicRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TopicRuleRepository extends JpaRepository<TopicRule, Long> {
    List<TopicRule> findByActiveTrue();
}
