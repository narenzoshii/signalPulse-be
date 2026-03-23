package com.signalpulse.service;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import com.signalpulse.entity.*;
import com.signalpulse.model.Article;
import com.signalpulse.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScannerService {
    private final RssFeedRepository rssFeedRepository;
    private final HtmlPageRepository htmlPageRepository;
    private final TopicRuleRepository topicRuleRepository;
    private final ScanResultRepository scanResultRepository;
    private final com.signalpulse.repository.ScannedArticleRepository scannedArticleRepository;
    private final NotificationService notificationService;
    private final JsonMapper jsonMapper;
    private final ConfigService configService;
    private final Map<String, java.util.regex.Pattern> patternCache = new java.util.concurrent.ConcurrentHashMap<>();

    public void runScan() {
        log.info("Starting comprehensive async scan at {}", LocalDateTime.now());
        long startTime = System.currentTimeMillis();
        
        List<Article> allArticles = Collections.synchronizedList(new ArrayList<>());
        
        LocalDateTime lastScanTime = scanResultRepository.findTop5ByOrderByTimestampDesc()
                .stream()
                .findFirst()
                .map(ScanResult::getTimestamp)
                .orElse(null);

        LocalDateTime cutoffDate;
        if (lastScanTime == null) {
            String lookbackStr = configService.getConfig("INITIAL_LOOKBACK_HOURS").orElse("1000");
            long lookback = 1000;
            try { lookback = Long.parseLong(lookbackStr); } catch (Exception ignored) {}
            cutoffDate = LocalDateTime.now().minusHours(lookback);
        } else {
            cutoffDate = lastScanTime;
        }

        log.info("cutoffDate: {}", cutoffDate);
        log.info("cutoffDateString: {}", cutoffDate.toString());

        // Parallel Scanning
        List<RssFeed> feeds = rssFeedRepository.findByEnabledTrue();
        List<HtmlPage> pages = htmlPageRepository.findByEnabledTrue();

        log.info("feeds: {}", feeds.toString());
        log.info("pages: {}", pages.toString());

        int maxRetries = Integer.parseInt(configService.getConfig("SCAN_MAX_RETRIES").orElse("3"));
        long retryInterval = Long.parseLong(configService.getConfig("SCAN_RETRY_INTERVAL_MS").orElse("2000"));

        Set<String> seenLinks = java.util.concurrent.ConcurrentHashMap.newKeySet();

        log.info("maxRetries: {}", maxRetries);
        log.info("retryInterval: {}", retryInterval);
        log.info("seenLinks: {}", seenLinks.toString());
        // Process RSS Feeds in Parallel
        feeds.parallelStream().forEach(feed -> {
            log.info("Processing RSS Feed: {}", feed.toString());
            allArticles.addAll(scanSingleRssFeed(feed, cutoffDate, maxRetries, retryInterval, seenLinks));
        });

        // Process HTML Pages in Parallel
        pages.parallelStream().forEach(page -> {
            log.info("Processing HTML Page: {}", page.toString());
            allArticles.addAll(scanSingleHtmlPage(page, cutoffDate, maxRetries, retryInterval, seenLinks));
        });
        
        // 3. Apply Topic Ranking
        scoreArticles(allArticles);

        // 4. Filter by minimum relevance score
        long ruleCount = topicRuleRepository.count();
        double minScore = 0.0;
        if (ruleCount > 0) {
            String minStr = configService.getConfig("MIN_RELEVANCE_SCORE").orElse("1.0");
            try { minScore = Double.parseDouble(minStr); } catch (Exception ignored) {}
        }
        final double threshold = minScore;
        List<Article> relevant = allArticles.stream()
            .filter(a -> a.getScore() >= threshold)
            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
            .toList();

        // 5. Store top N
        String topNStr = configService.getConfig("TOP_N_ARTICLES").orElse("10");
        int topN = 10;
        try { topN = Integer.parseInt(topNStr); } catch (Exception ignored) {}
        List<Article> topArticles = relevant.stream().limit(topN).toList();
        
        long duration = System.currentTimeMillis() - startTime;
        saveResults(topArticles, duration, allArticles.size());
        
        if (!topArticles.isEmpty()) {
            String notifyEmail = configService.getConfig("NOTIFY_RECIPIENTS").orElse("admin@signalpulse.com");
            log.info("NotifyEmail: {}", notifyEmail);
            StringBuilder sb = new StringBuilder("Here are the latest relevant articles found:\n\n");
            for (Article a : topArticles) {
                sb.append("- ").append(a.getTitle()).append("\n")
                  .append("  ").append(a.getLink()).append("\n\n");
            }
            log.info("Articles: {}", sb.toString());

            notificationService.sendTopArticles(notifyEmail, sb.toString(), cutoffDate.toString());
        }

        log.info("Scan completed in {}ms. Found {} articles ({} passing score).", duration, allArticles.size(), relevant.size());
    }


    private List<Article> scanSingleRssFeed(RssFeed feed, LocalDateTime cutoffDate, int maxRetries, long retryInterval, Set<String> seenLinks) {
        List<Article> articles = new ArrayList<>();
        boolean success = false;
        for (int attempt = 0; attempt <= maxRetries && !success; attempt++) {
            try {
                java.net.HttpURLConnection con = (java.net.HttpURLConnection) new URL(feed.getUrl()).openConnection();
                con.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
                con.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*");
                con.setConnectTimeout(10000);
                con.setReadTimeout(15000);

                SyndFeed syndFeed = new SyndFeedInput().build(new XmlReader(con));
                for (SyndEntry entry : syndFeed.getEntries()) {
                    Date pubDate = entry.getPublishedDate();
                    if (pubDate != null) {
                        LocalDateTime publishedDateTime = new java.sql.Timestamp(pubDate.getTime()).toLocalDateTime();
                        if (publishedDateTime.isBefore(cutoffDate)) continue;
                    }

                    String link = entry.getLink();
                    if (link == null || link.isBlank() || !seenLinks.add(link) || scannedArticleRepository.existsByLink(link)) continue;

                    Article article = new Article();
                    article.setTitle(entry.getTitle());
                    article.setLink(link);
                    article.setDescription(entry.getDescription() != null ? entry.getDescription().getValue() : "");
                    article.setSource(feed.getName());
                    double catWeight = feed.getCategory() != null ? feed.getCategory().getWeight() : 0.0;
                    article.setBaseScore(feed.getTrust() + catWeight);
                    article.setPublishedDate(entry.getPublishedDate());
                    articles.add(article);
                }
                success = true;
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    try { Thread.sleep((attempt + 1) * retryInterval); } catch (InterruptedException ignored) {}
                } else {
                    log.warn("Failed RSS feed {}: {}. URL: {}", feed.getName(), e.getMessage(), feed.getUrl());
                }
            }
        }
        return articles;
    }

    private List<Article> scanSingleHtmlPage(HtmlPage page, LocalDateTime cutoffDate, int maxRetries, long retryInterval, Set<String> seenLinks) {
        if (page.getListSelector() == null || page.getListSelector().isBlank()) return Collections.emptyList();
        
        List<Article> articles = new ArrayList<>();
        boolean success = false;
        for (int attempt = 0; attempt <= maxRetries && !success; attempt++) {
            try {
                org.jsoup.Connection con = org.jsoup.Jsoup.connect(page.getUrl())
                        .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                        .timeout(15000)
                        .followRedirects(true);
                
                org.jsoup.Connection.Response response = con.execute();
                if (response.statusCode() != 200) {
                    throw new Exception("HTTP status " + response.statusCode());
                }

                org.jsoup.nodes.Document doc = response.parse();
                org.jsoup.select.Elements elements = doc.select(page.getListSelector());
                
                if (elements.isEmpty()) {
                    log.warn("No elements found for selector '{}' on HTML page {}. URL: {}", page.getListSelector(), page.getName(), page.getUrl());
                }

                for (org.jsoup.nodes.Element el : elements) {
                    String title = page.getTitleSelector() != null && !page.getTitleSelector().isBlank() 
                            ? el.select(page.getTitleSelector()).text() : "";
                    String link = page.getLinkSelector() != null && !page.getLinkSelector().isBlank()
                            ? el.select(page.getLinkSelector()).attr("abs:href") : "";

                    if (link.isBlank() || !seenLinks.add(link) || scannedArticleRepository.existsByLink(link)) continue;

                    Article article = new Article();
                    article.setTitle(title);
                    article.setLink(link);
                    article.setSource(page.getName());
                    
                    // Simple description extraction: find the first paragraph or generic text after the title
                    String description = el.text().replace(title, "").trim();
                    if (description.isEmpty()) {
                         description = doc.select("meta[name=description]").attr("content");
                         if (description.isEmpty()) {
                             description = title; // fallback to title
                         }
                    }

                    if (description.length() > 300) {
                        description = description.substring(0, 297) + "...";
                    }
                    article.setDescription(description);

                    double catWeight = page.getCategory() != null ? page.getCategory().getWeight() : 0.0;
                    article.setBaseScore(page.getTrust() + catWeight);
                    articles.add(article);
                }
                success = true;
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    try { Thread.sleep((attempt + 1) * retryInterval); } catch (InterruptedException ignored) {}
                } else {
                    log.error("Final failure for HTML page {}: {}. URL: {}", page.getName(), e.getMessage(), page.getUrl());
                }
            }
        }
        return articles;
    }

    void scoreArticles(List<Article> articles) {
        List<TopicRule> rules = topicRuleRepository.findByActiveTrue();
        for (Article article : articles) {
            double additionalScore = 0;
            String textToSearch = (article.getTitle() + " " + article.getDescription()).toLowerCase();
            
            for (TopicRule rule : rules) {
                boolean ruleMatched = false;
                for (String patternStr : rule.getPatterns()) {
                    try {
                        java.util.regex.Pattern pattern = patternCache.computeIfAbsent(patternStr, p -> 
                            java.util.regex.Pattern.compile(p, java.util.regex.Pattern.CASE_INSENSITIVE));
                        
                        if (pattern.matcher(textToSearch).find()) {
                            additionalScore += rule.getWeight();
                            ruleMatched = true;
                            // Option: continue to other patterns if you want to reward multiple matches within a rule
                            // For now, let's stick to once per rule to avoid over-weighting
                            break; 
                        }
                    } catch (java.util.regex.PatternSyntaxException e) {
                        log.warn("Invalid regex pattern in rule {}: {}", rule.getTopicKey(), patternStr);
                        // Fallback to simple contains if regex fails
                        if (textToSearch.contains(patternStr.toLowerCase())) {
                            additionalScore += rule.getWeight();
                            ruleMatched = true;
                            break;
                        }
                    }
                }
            }
            article.setScore(article.getBaseScore() + additionalScore);
        }
    }

    private void saveResults(List<Article> topArticles, long durationMs, int totalFound) {
        try {
            ScanResult result = new ScanResult();
            result.setTimestamp(LocalDateTime.now());
            result.setDurationMs(durationMs);
            result.setArticlesFound(totalFound);
            result.setNewItemsCount(topArticles.size());
            result.setStatus("SUCCESS");
            final ScanResult savedResult = scanResultRepository.save(result);

            // Persist to ScannedArticle table
            List<com.signalpulse.entity.ScannedArticle> entities = topArticles.stream().map(a -> {
                com.signalpulse.entity.ScannedArticle sa = new com.signalpulse.entity.ScannedArticle();
                sa.setTitle(a.getTitle());
                sa.setLink(a.getLink());
                sa.setDescription(a.getDescription());
                sa.setSource(a.getSource());
                sa.setScore(a.getScore());
                sa.setPublishedAt(a.getPublishedDate() != null ? 
                        new java.sql.Timestamp(a.getPublishedDate().getTime()).toLocalDateTime() : null);
                sa.setScanResult(savedResult);
                return sa;
            }).toList();
            scannedArticleRepository.saveAll(entities);

        } catch (Exception e) {
            log.error("Error saving scan results", e);
        }
    }
}
