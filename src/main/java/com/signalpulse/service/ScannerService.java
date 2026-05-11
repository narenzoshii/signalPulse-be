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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScannerService {
    private final RssFeedRepository rssFeedRepository;
    private final HtmlPageRepository htmlPageRepository;
    private final TopicRuleRepository topicRuleRepository;
    private final ScanResultRepository scanResultRepository;
    private final ScannedArticleRepository scannedArticleRepository;
    private final NotificationService notificationService;
    private final ConfigService configService;

    @Qualifier("scanExecutor")
    private final ExecutorService scanExecutor;

    @Value("${app.scan.task-timeout-seconds:120}")
    private long perTaskTimeoutSeconds;

    private final Map<String, java.util.regex.Pattern> patternCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicBoolean scanInProgress = new AtomicBoolean(false);

    public void runScan() {
        runScan("SCHEDULED");
    }

    public void runScan(String triggerType) {
        if (!scanInProgress.compareAndSet(false, true)) {
            log.warn("Skipping {} scan: another scan is already in progress", triggerType);
            return;
        }
        try {
            doRunScan(triggerType);
        } finally {
            scanInProgress.set(false);
        }
    }

    private void doRunScan(String triggerType) {
        log.info("Starting scan ({}) at {}", triggerType, LocalDateTime.now());
        long startTime = System.currentTimeMillis();

        List<Article> allArticles = Collections.synchronizedList(new ArrayList<>());

        LocalDateTime lastScanTime = scanResultRepository.findTop5ByOrderByTimestampDesc()
                .stream()
                .findFirst()
                .map(ScanResult::getTimestamp)
                .orElse(null);

        LocalDateTime cutoffDate;
        if (lastScanTime == null) {
            long lookback = parseLongConfig("INITIAL_LOOKBACK_HOURS", 1000L);
            cutoffDate = LocalDateTime.now().minusHours(lookback);
        } else {
            cutoffDate = lastScanTime;
        }

        log.debug("Scan cutoffDate: {}", cutoffDate);

        List<RssFeed> feeds = rssFeedRepository.findByEnabledTrue();
        List<HtmlPage> pages = htmlPageRepository.findByEnabledTrue();

        int maxRetries = (int) parseLongConfig("SCAN_MAX_RETRIES", 3L);
        long retryInterval = parseLongConfig("SCAN_RETRY_INTERVAL_MS", 2000L);

        // Batch dedup: load every link we've ever stored. HTML pages without
        // pub dates re-surface on every scrape, so a time-bounded window would
        // re-insert the same article each scan.
        Set<String> knownLinks = java.util.concurrent.ConcurrentHashMap.newKeySet();
        knownLinks.addAll(scannedArticleRepository.findAllLinks());
        log.debug("Loaded {} known links for dedup", knownLinks.size());

        // Dispatch all feed/page fetches on the dedicated scanExecutor so we
        // don't starve the common ForkJoinPool with blocking outbound I/O.
        List<CompletableFuture<List<Article>>> futures = new ArrayList<>(feeds.size() + pages.size());
        Function<RssFeed, List<Article>> feedJob = f -> scanSingleRssFeed(f, cutoffDate, maxRetries, retryInterval, knownLinks);
        Function<HtmlPage, List<Article>> pageJob = p -> scanSingleHtmlPage(p, cutoffDate, maxRetries, retryInterval, knownLinks);
        for (RssFeed feed : feeds) {
            futures.add(CompletableFuture.supplyAsync(() -> feedJob.apply(feed), scanExecutor));
        }
        for (HtmlPage page : pages) {
            futures.add(CompletableFuture.supplyAsync(() -> pageJob.apply(page), scanExecutor));
        }
        for (CompletableFuture<List<Article>> future : futures) {
            try {
                allArticles.addAll(future.get(perTaskTimeoutSeconds, TimeUnit.SECONDS));
            } catch (TimeoutException e) {
                future.cancel(true);
                log.warn("Scan task exceeded {}s timeout, cancelled.", perTaskTimeoutSeconds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Scan interrupted while awaiting tasks.");
                break;
            } catch (Exception e) {
                log.warn("Scan task failed: {}", e.getMessage());
            }
        }

        scoreArticles(allArticles);

        long ruleCount = topicRuleRepository.count();
        double minScore = 0.0;
        if (ruleCount > 0) {
            minScore = parseDoubleConfig("MIN_RELEVANCE_SCORE", 1.0);
        }
        final double threshold = minScore;
        List<Article> relevant = allArticles.stream()
            .filter(a -> a.getScore() >= threshold)
            .sorted((a, b) -> Double.compare(b.getScore(), a.getScore()))
            .toList();

        int topN = (int) parseLongConfig("TOP_N_ARTICLES", 10L);
        List<Article> topArticles = relevant.stream().limit(topN).toList();

        long duration = System.currentTimeMillis() - startTime;
        saveResults(topArticles, duration, allArticles.size(), triggerType);

        if (!topArticles.isEmpty()) {
            String notifyEmail = configService.getConfig("NOTIFY_RECIPIENTS").orElse("admin@signalpulse.com");
            StringBuilder sb = new StringBuilder("Here are the latest relevant articles found:\n\n");
            for (Article a : topArticles) {
                sb.append("- ").append(a.getTitle()).append("\n")
                  .append("  ").append(a.getLink()).append("\n\n");
            }
            notificationService.sendTopArticles(notifyEmail, sb.toString(), cutoffDate.toString());
        }

        log.info("Scan completed in {}ms. Found {} articles ({} passing score).",
                duration, allArticles.size(), relevant.size());
    }

    private long parseLongConfig(String key, long defaultValue) {
        try {
            return Long.parseLong(configService.getConfig(key).orElse(String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            log.warn("Invalid numeric config '{}', using default {}", key, defaultValue);
            return defaultValue;
        }
    }

    private double parseDoubleConfig(String key, double defaultValue) {
        try {
            return Double.parseDouble(configService.getConfig(key).orElse(String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            log.warn("Invalid numeric config '{}', using default {}", key, defaultValue);
            return defaultValue;
        }
    }

    private List<Article> scanSingleRssFeed(RssFeed feed, LocalDateTime cutoffDate, int maxRetries, long retryInterval, Set<String> knownLinks) {
        List<Article> articles = new ArrayList<>();
        boolean success = false;
        for (int attempt = 0; attempt <= maxRetries && !success; attempt++) {
            java.net.HttpURLConnection con = null;
            try {
                con = (java.net.HttpURLConnection) URI.create(feed.getUrl()).toURL().openConnection();
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
                    if (link == null || link.isBlank() || !knownLinks.add(link)) continue;

                    Article article = new Article();
                    article.setTitle(entry.getTitle());
                    article.setLink(link);
                    article.setDescription(entry.getDescription() != null ? entry.getDescription().getValue() : "");
                    article.setSource(feed.getName());
                    double catWeight = feed.getCategory() != null && feed.getCategory().getWeight() != null ? feed.getCategory().getWeight() : 0.0;
                    article.setBaseScore(feed.getTrust() + catWeight);
                    article.setPublishedDate(entry.getPublishedDate());
                    articles.add(article);
                }
                success = true;
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    try { Thread.sleep((attempt + 1) * retryInterval); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else {
                    log.warn("Failed RSS feed {}: {}. URL: {}", feed.getName(), e.getMessage(), feed.getUrl());
                }
            } finally {
                if (con != null) con.disconnect();
            }
        }
        return articles;
    }

    private List<Article> scanSingleHtmlPage(HtmlPage page, LocalDateTime cutoffDate, int maxRetries, long retryInterval, Set<String> knownLinks) {
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

                    if (link.isBlank() || !knownLinks.add(link)) continue;

                    Article article = new Article();
                    article.setTitle(title);
                    article.setLink(link);
                    article.setSource(page.getName());

                    String description = el.text().replace(title, "").trim();
                    if (description.isEmpty()) {
                         description = doc.select("meta[name=description]").attr("content");
                         if (description.isEmpty()) {
                             description = title;
                         }
                    }

                    if (description.length() > 300) {
                        description = description.substring(0, 297) + "...";
                    }
                    article.setDescription(description);

                    double catWeight = page.getCategory() != null && page.getCategory().getWeight() != null ? page.getCategory().getWeight() : 0.0;
                    article.setBaseScore(page.getTrust() + catWeight);
                    articles.add(article);
                }
                success = true;
            } catch (Exception e) {
                if (attempt < maxRetries) {
                    try { Thread.sleep((attempt + 1) * retryInterval); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
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
                for (String patternStr : rule.getPatterns()) {
                    try {
                        java.util.regex.Pattern pattern = patternCache.computeIfAbsent(patternStr, p ->
                            java.util.regex.Pattern.compile(p, java.util.regex.Pattern.CASE_INSENSITIVE));

                        if (pattern.matcher(textToSearch).find()) {
                            additionalScore += rule.getWeight();
                            break;
                        }
                    } catch (java.util.regex.PatternSyntaxException e) {
                        log.warn("Invalid regex pattern in rule {}: {}", rule.getTopicKey(), patternStr);
                        if (textToSearch.contains(patternStr.toLowerCase())) {
                            additionalScore += rule.getWeight();
                            break;
                        }
                    }
                }
            }
            article.setScore(article.getBaseScore() + additionalScore);
        }
    }

    private void saveResults(List<Article> topArticles, long durationMs, int totalFound, String triggerType) {
        try {
            ScanResult result = new ScanResult();
            result.setTimestamp(LocalDateTime.now());
            result.setDurationMs(durationMs);
            result.setArticlesFound(totalFound);
            result.setNewItemsCount(topArticles.size());
            result.setStatus("SUCCESS");
            result.setTriggerType(triggerType);
            final ScanResult savedResult = scanResultRepository.save(result);

            List<ScannedArticle> entities = topArticles.stream().map(a -> {
                ScannedArticle sa = new ScannedArticle();
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
