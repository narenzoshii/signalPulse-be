package com.signalpulse.service;

import com.signalpulse.entity.Category;
import com.signalpulse.entity.RssFeed;
import com.signalpulse.repository.CategoryRepository;
import com.signalpulse.repository.RssFeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FeedService {
    private final RssFeedRepository rssFeedRepository;
    private final CategoryRepository categoryRepository;

    @Transactional(readOnly = true)
    public Page<RssFeed> getAllFeeds(Pageable pageable) {
        return rssFeedRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<RssFeed> getAllFeeds() {
        return rssFeedRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<RssFeed> findFeedById(Long id) {
        return rssFeedRepository.findById(id);
    }

    public RssFeed saveFeed(RssFeed feed) {
        return rssFeedRepository.save(feed);
    }

    public void deleteFeed(Long id) {
        rssFeedRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
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
