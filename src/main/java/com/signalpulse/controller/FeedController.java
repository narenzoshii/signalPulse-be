package com.signalpulse.controller;

import com.signalpulse.entity.Category;
import com.signalpulse.entity.RssFeed;
import com.signalpulse.service.FeedService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/feeds")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FeedController {
    private final FeedService feedService;
    
    @GetMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('OP_READ_ALL')")
    public List<RssFeed> getAllFeeds() {
        return feedService.getAllFeeds();
    }

    
    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('OP_WRITE_SOURCES')")
    public RssFeed saveFeed(@RequestBody RssFeed feed) {
        return feedService.saveFeed(feed);
    }

    
    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('OP_WRITE_SOURCES')")
    public void deleteFeed(@PathVariable Long id) {
        feedService.deleteFeed(id);
    }

    
    @GetMapping("/categories")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('OP_READ_ALL')")
    public List<Category> getAllCategories() {
        return feedService.getAllCategories();
    }

    
    @PostMapping("/categories")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('OP_WRITE_SOURCES')")
    public Category saveCategory(@RequestBody Category category) {
        return feedService.saveCategory(category);
    }

    
    @DeleteMapping("/categories/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('OP_WRITE_SOURCES')")
    public void deleteCategory(@PathVariable Long id) {
        feedService.deleteCategory(id);
    }

}
