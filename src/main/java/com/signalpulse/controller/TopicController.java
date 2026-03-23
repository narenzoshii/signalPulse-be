package com.signalpulse.controller;

import com.signalpulse.entity.TopicRule;
import com.signalpulse.repository.TopicRuleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TopicController {
    private final TopicRuleRepository repository;

    @GetMapping
    @PreAuthorize("hasAuthority('OP_READ_ALL')")
    public List<TopicRule> getAll() { return repository.findAll(); }


    @PostMapping
    @PreAuthorize("hasAuthority('OP_WRITE_RULES')")
    public TopicRule save(@RequestBody TopicRule topic) { return repository.save(topic); }


    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('OP_WRITE_RULES')")
    public void delete(@PathVariable Long id) { repository.deleteById(id); }

}
