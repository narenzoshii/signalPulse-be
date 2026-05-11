package com.signalpulse.controller;

import com.signalpulse.dto.PageResponse;
import com.signalpulse.dto.TopicRuleRequest;
import com.signalpulse.entity.TopicRule;
import com.signalpulse.repository.TopicRuleRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
public class TopicController {
    private static final int MAX_PAGE_SIZE = 200;

    private final TopicRuleRepository repository;

    @GetMapping
    @PreAuthorize("hasAuthority('OP_READ_ALL')")
    public PageResponse<TopicRule> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(defaultValue = "topicKey") String sort) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, MAX_PAGE_SIZE), Sort.by(sort).ascending());
        return PageResponse.from(repository.findAll(pageable));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('OP_WRITE_RULES')")
    public TopicRule save(@Valid @RequestBody TopicRuleRequest body) {
        validatePatterns(body);
        TopicRule rule = body.getId() == null
                ? new TopicRule()
                : repository.findById(body.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Topic rule not found: " + body.getId()));
        rule.setTopicKey(body.getTopicKey());
        rule.setDescription(body.getDescription());
        rule.setWeight(body.getWeight());
        rule.setPatterns(new ArrayList<>(body.getPatterns()));
        rule.setActive(body.isActive());
        return repository.save(rule);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('OP_WRITE_RULES')")
    public void delete(@PathVariable Long id) {
        repository.deleteById(id);
    }

    private void validatePatterns(TopicRuleRequest body) {
        for (String pattern : body.getPatterns()) {
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Invalid regex pattern '" + pattern + "': " + e.getDescription());
            }
        }
    }
}
