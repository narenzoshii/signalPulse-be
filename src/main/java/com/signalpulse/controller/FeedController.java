package com.signalpulse.controller;

import com.signalpulse.dto.CategoryRequest;
import com.signalpulse.dto.FeedRequest;
import com.signalpulse.dto.PageResponse;
import com.signalpulse.entity.Category;
import com.signalpulse.entity.RssFeed;
import com.signalpulse.repository.CategoryRepository;
import com.signalpulse.service.FeedService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feeds")
@RequiredArgsConstructor
public class FeedController {
    private static final int MAX_PAGE_SIZE = 200;

    private final FeedService feedService;
    private final CategoryRepository categoryRepository;

    @GetMapping
    @PreAuthorize("hasAuthority('OP_READ_ALL')")
    public PageResponse<RssFeed> getAllFeeds(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "name") String sort) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, MAX_PAGE_SIZE), Sort.by(sort).ascending());
        return PageResponse.from(feedService.getAllFeeds(pageable));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('OP_WRITE_SOURCES')")
    public RssFeed saveFeed(@Valid @RequestBody FeedRequest body) {
        RssFeed feed = body.getId() == null
                ? new RssFeed()
                : feedService.findFeedById(body.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Feed not found: " + body.getId()));
        feed.setName(body.getName());
        feed.setUrl(body.getUrl());
        feed.setDescription(body.getDescription());
        feed.setTrust(body.getTrust());
        feed.setEnabled(body.isEnabled());
        feed.setCategory(resolveCategory(body.getCategoryId()));
        return feedService.saveFeed(feed);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('OP_WRITE_SOURCES')")
    public void deleteFeed(@PathVariable Long id) {
        feedService.deleteFeed(id);
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('OP_READ_ALL')")
    public PageResponse<Category> getAllCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, MAX_PAGE_SIZE), Sort.by("name").ascending());
        return PageResponse.from(categoryRepository.findAll(pageable));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('OP_WRITE_SOURCES')")
    public Category saveCategory(@Valid @RequestBody CategoryRequest body) {
        Category cat = body.getId() == null
                ? new Category()
                : categoryRepository.findById(body.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Category not found: " + body.getId()));
        cat.setName(body.getName());
        cat.setDescription(body.getDescription());
        cat.setWeight(body.getWeight());
        return categoryRepository.save(cat);
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasAuthority('OP_WRITE_SOURCES')")
    public void deleteCategory(@PathVariable Long id) {
        feedService.deleteCategory(id);
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found: " + categoryId));
    }
}
