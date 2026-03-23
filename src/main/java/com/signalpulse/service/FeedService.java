package com.signalpulse.service;

import com.signalpulse.entity.Category;
import com.signalpulse.entity.RssFeed;
import com.signalpulse.repository.CategoryRepository;
import com.signalpulse.repository.RssFeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FeedService {
    private final RssFeedRepository rssFeedRepository;
    private final CategoryRepository categoryRepository;
    
    public List<RssFeed> getAllFeeds() {
        return rssFeedRepository.findAll();
    }
    
    public RssFeed saveFeed(RssFeed feed) {
        return rssFeedRepository.save(feed);
    }
    
    public void deleteFeed(Long id) {
        rssFeedRepository.deleteById(id);
    }
    
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }
    
    public Category saveCategory(Category category) {
        return categoryRepository.save(category);
    }
    
    public void deleteCategory(Long id) {
        categoryRepository.deleteById(id);
    }
}
