package com.signalpulse.service;

import com.signalpulse.entity.TopicRule;
import com.signalpulse.model.Article;
import com.signalpulse.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class ScannerServiceTest {

    @Mock
    private RssFeedRepository rssFeedRepository;
    @Mock
    private HtmlPageRepository htmlPageRepository;
    @Mock
    private TopicRuleRepository topicRuleRepository;
    @Mock
    private ScanResultRepository scanResultRepository;
    @Mock
    private ScannedArticleRepository scannedArticleRepository;
    @Mock
    private NotificationService notificationService;
    @Mock
    private JsonMapper jsonMapper;
    @Mock
    private ConfigService configService;

    @InjectMocks
    private ScannerService scannerService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testScoreArticlesWithRegex() {
        // Arrange
        Article article = new Article();
        article.setTitle("Nepal Rastra Bank issues new Digital Lending Guidelines");
        article.setDescription("The new framework aims to regulate BNPL and online loans.");
        article.setBaseScore(5.0);

        TopicRule rule1 = new TopicRule();
        rule1.setTopicKey("lending_regulation");
        rule1.setWeight(10.0);
        rule1.setPatterns(Arrays.asList("\\b(nrb|nepal rastra bank) (circular|guideline|directive|framework)\\b"));
        rule1.setActive(true);

        TopicRule rule2 = new TopicRule();
        rule2.setTopicKey("bnpl");
        rule2.setWeight(8.0);
        rule2.setPatterns(Arrays.asList("\\b(bnpl|buy now pay later)\\b"));
        rule2.setActive(true);

        TopicRule rule3 = new TopicRule();
        rule3.setTopicKey("no_match");
        rule3.setWeight(5.0);
        rule3.setPatterns(Arrays.asList("\\b(unrelated)\\b"));
        rule3.setActive(true);

        when(topicRuleRepository.findByActiveTrue()).thenReturn(Arrays.asList(rule1, rule2, rule3));

        List<Article> articles = Collections.singletonList(article);

        // Act
        scannerService.scoreArticles(articles);

        // Assert
        // Base score (5.0) + rule1 (10.0) + rule2 (8.0) = 23.0
        assertEquals(23.0, article.getScore(), "Score should include base score and matched rule weights");
    }

    @Test
    void testScoreArticlesWithCaseInsensitivity() {
        // Arrange
        Article article = new Article();
        article.setTitle("LENDING TECH Innovations");
        article.setDescription("");
        article.setBaseScore(1.0);

        TopicRule rule1 = new TopicRule();
        rule1.setTopicKey("lending_tech");
        rule1.setWeight(5.0);
        rule1.setPatterns(Arrays.asList("lending tech"));
        rule1.setActive(true);

        when(topicRuleRepository.findByActiveTrue()).thenReturn(Collections.singletonList(rule1));

        List<Article> articles = Collections.singletonList(article);

        // Act
        scannerService.scoreArticles(articles);

        // Assert
        assertEquals(6.0, article.getScore(), "Score should be case insensitive");
    }
}
