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

import java.io.InputStream;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.zip.GZIPInputStream;

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
    private final HtmlAutoDiscovery autoDiscovery;
    private final SourceHealthService healthService;

    @Qualifier("scanExecutor")
    private final ExecutorService scanExecutor;

    @Value("${app.scan.task-timeout-seconds:120}")
    private long perTaskTimeoutSeconds;

    private final Map<String, java.util.regex.Pattern> patternCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicBoolean scanInProgress = new AtomicBoolean(false);

    /** Mirror of ScannedArticle.link column length. Links longer than this are dropped. */
    private static final int MAX_LINK_LENGTH = 768;

    // Browser-typical request headers. Many sites (Cloudflare, Akamai, Datadome)
    // treat a UA-only request as a bot and return 403; sending the full set of
    // headers a real Chrome sends dramatically reduces those rejections.
    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    private static final String ACCEPT_LANGUAGE = "en-US,en;q=0.9";
    private static final String SEC_CH_UA = "\"Chromium\";v=\"124\", \"Google Chrome\";v=\"124\", \"Not-A.Brand\";v=\"99\"";
    private static final String SEC_CH_UA_PLATFORM = "\"macOS\"";

    /** Apply the full set of "Chrome-shaped" headers to a Jsoup connection. */
    private static org.jsoup.Connection applyBrowserHeaders(org.jsoup.Connection con, String accept) {
        return con
                .userAgent(USER_AGENT)
                .header("Accept", accept)
                .header("Accept-Language", ACCEPT_LANGUAGE)
                .header("Accept-Encoding", "gzip, deflate")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Sec-Ch-Ua", SEC_CH_UA)
                .header("Sec-Ch-Ua-Mobile", "?0")
                .header("Sec-Ch-Ua-Platform", SEC_CH_UA_PLATFORM)
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1");
    }

    /** Apply the same headers to a raw HttpURLConnection (used by Rome / RSS). */
    private static void applyBrowserHeaders(java.net.HttpURLConnection con, String accept) {
        con.setRequestProperty("User-Agent", USER_AGENT);
        con.setRequestProperty("Accept", accept);
        con.setRequestProperty("Accept-Language", ACCEPT_LANGUAGE);
        con.setRequestProperty("Accept-Encoding", "gzip, deflate");
        con.setRequestProperty("Cache-Control", "no-cache");
        con.setRequestProperty("Pragma", "no-cache");
        con.setRequestProperty("Upgrade-Insecure-Requests", "1");
        con.setRequestProperty("Sec-Ch-Ua", SEC_CH_UA);
        con.setRequestProperty("Sec-Ch-Ua-Mobile", "?0");
        con.setRequestProperty("Sec-Ch-Ua-Platform", SEC_CH_UA_PLATFORM);
        con.setRequestProperty("Sec-Fetch-Dest", "document");
        con.setRequestProperty("Sec-Fetch-Mode", "navigate");
        con.setRequestProperty("Sec-Fetch-Site", "none");
        con.setRequestProperty("Sec-Fetch-User", "?1");
    }

    /** HTTP status codes for which a retry could plausibly help. */
    private static boolean isRetryable(int status) {
        if (status >= 500 && status < 600) return true;  // transient server error
        if (status == 408 || status == 429) return true; // request timeout / rate limit
        return false;
    }

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
        int maxPerSource = (int) parseLongConfig("MAX_PER_SOURCE", 3L);
        List<Article> topArticles = capPerSource(relevant, maxPerSource, topN);

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

    /**
     * Greedy fairness cap: walk the score-sorted list and keep at most
     * {@code maxPerSource} hits from any one source, stopping once we've
     * collected {@code topN} articles. Preserves top-overall ordering while
     * preventing a single chatty feed from dominating the digest.
     */
    private List<Article> capPerSource(List<Article> sortedByScoreDesc, int maxPerSource, int topN) {
        if (maxPerSource <= 0) return sortedByScoreDesc.stream().limit(topN).toList();
        Map<String, Integer> sourceCounts = new HashMap<>();
        List<Article> out = new ArrayList<>(topN);
        for (Article a : sortedByScoreDesc) {
            if (out.size() >= topN) break;
            String source = a.getSource() != null ? a.getSource() : "(unknown)";
            int count = sourceCounts.getOrDefault(source, 0);
            if (count >= maxPerSource) continue;
            sourceCounts.put(source, count + 1);
            out.add(a);
        }
        return out;
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
        String lastError = null;
        for (int attempt = 0; attempt <= maxRetries && !success; attempt++) {
            java.net.HttpURLConnection con = null;
            try {
                con = (java.net.HttpURLConnection) URI.create(feed.getUrl()).toURL().openConnection();
                applyBrowserHeaders(con,
                        "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.9, */*;q=0.5");
                con.setInstanceFollowRedirects(true);
                con.setConnectTimeout(10000);
                con.setReadTimeout(15000);

                int status = con.getResponseCode();
                if (status >= 400) {
                    if (!isRetryable(status)) {
                        lastError = "HTTP " + status + " (permanent)";
                        log.warn("Permanent failure for RSS feed {} (status {}, not retrying). URL: {}",
                                feed.getName(), status, feed.getUrl());
                        break;
                    }
                    throw new java.io.IOException("HTTP " + status);
                }

                InputStream stream = con.getInputStream();
                String encoding = con.getContentEncoding();
                if ("gzip".equalsIgnoreCase(encoding)) {
                    stream = new GZIPInputStream(stream);
                }

                SyndFeed syndFeed;
                try (InputStream s = stream) {
                    syndFeed = new SyndFeedInput().build(new XmlReader(s));
                }

                for (SyndEntry entry : syndFeed.getEntries()) {
                    Date pubDate = entry.getPublishedDate();
                    if (pubDate != null) {
                        LocalDateTime publishedDateTime = new java.sql.Timestamp(pubDate.getTime()).toLocalDateTime();
                        if (publishedDateTime.isBefore(cutoffDate)) continue;
                    }

                    String link = entry.getLink();
                    if (link == null || link.isBlank() || link.length() > MAX_LINK_LENGTH || !knownLinks.add(link)) continue;

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
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                if (attempt < maxRetries) {
                    try { Thread.sleep((attempt + 1) * retryInterval); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else {
                    log.warn("Failed RSS feed {}: {}. URL: {}", feed.getName(), e.getMessage(), feed.getUrl());
                }
            } finally {
                if (con != null) con.disconnect();
            }
        }

        // Record health exactly once for this scan attempt.
        try {
            if (success) healthService.recordRssSuccess(feed.getId(), articles.size());
            else healthService.recordRssFailure(feed.getId(), lastError);
        } catch (Exception e) {
            log.warn("Could not record health for RSS feed {}: {}", feed.getName(), e.getMessage());
        }
        return articles;
    }

    private List<Article> scanSingleHtmlPage(HtmlPage page, LocalDateTime cutoffDate, int maxRetries, long retryInterval, Set<String> knownLinks) {
        boolean isAuto = page.getDiscoveryMode() == null || "auto".equalsIgnoreCase(page.getDiscoveryMode());
        if (!isAuto && (page.getListSelector() == null || page.getListSelector().isBlank())) {
            log.warn("HTML page '{}' is in manual mode but has no listSelector; skipping.", page.getName());
            return Collections.emptyList();
        }

        List<Article> articles = new ArrayList<>();
        boolean success = false;
        String lastError = null;
        for (int attempt = 0; attempt <= maxRetries && !success; attempt++) {
            try {
                org.jsoup.Connection con = applyBrowserHeaders(
                        org.jsoup.Jsoup.connect(page.getUrl()),
                        "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                org.jsoup.Connection.Response response = con
                        .ignoreHttpErrors(true)
                        .timeout(15000)
                        .followRedirects(true)
                        .execute();
                int status = response.statusCode();
                if (status >= 400) {
                    if (!isRetryable(status)) {
                        lastError = "HTTP " + status + " (permanent)";
                        log.warn("Permanent failure for HTML page {} (status {}, not retrying). URL: {}",
                                page.getName(), status, page.getUrl());
                        break;
                    }
                    throw new Exception("HTTP status " + status);
                }

                org.jsoup.nodes.Document doc = response.parse();
                if (isAuto) {
                    articles.addAll(extractAuto(page, doc, knownLinks));
                } else {
                    articles.addAll(extractManual(page, doc, knownLinks));
                }
                success = true;
            } catch (Exception e) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                if (attempt < maxRetries) {
                    try { Thread.sleep((attempt + 1) * retryInterval); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                } else {
                    log.error("Final failure for HTML page {}: {}. URL: {}", page.getName(), e.getMessage(), page.getUrl());
                }
            }
        }

        try {
            if (success) healthService.recordHtmlSuccess(page.getId(), articles.size());
            else healthService.recordHtmlFailure(page.getId(), lastError);
        } catch (Exception e) {
            log.warn("Could not record health for HTML page {}: {}", page.getName(), e.getMessage());
        }
        return articles;
    }

    private List<Article> extractAuto(HtmlPage page, org.jsoup.nodes.Document doc, Set<String> knownLinks) {
        HtmlAutoDiscovery.DiscoveryResult result = autoDiscovery.discover(doc, page.getUrl());
        if (result.getArticles().isEmpty()) {
            log.warn("Auto-discovery found no articles on {} ({})", page.getName(), page.getUrl());
            return Collections.emptyList();
        }
        double catWeight = page.getCategory() != null && page.getCategory().getWeight() != null ? page.getCategory().getWeight() : 0.0;
        List<Article> out = new ArrayList<>();
        for (HtmlAutoDiscovery.DiscoveredArticle da : result.getArticles()) {
            String link = da.getLink();
            if (link == null || link.isBlank() || link.length() > MAX_LINK_LENGTH || !knownLinks.add(link)) continue;
            Article article = new Article();
            article.setTitle(da.getTitle());
            article.setLink(link);
            article.setDescription(da.getDescription());
            article.setSource(page.getName());
            article.setBaseScore(page.getTrust() + catWeight);
            out.add(article);
        }
        log.debug("Auto-discovered {} articles on {} (signature: {})", out.size(), page.getName(), result.getDiscoveredSelector());
        return out;
    }

    private List<Article> extractManual(HtmlPage page, org.jsoup.nodes.Document doc, Set<String> knownLinks) {
        org.jsoup.select.Elements elements = doc.select(page.getListSelector());
        if (elements.isEmpty()) {
            log.warn("No elements found for selector '{}' on HTML page {}. URL: {}", page.getListSelector(), page.getName(), page.getUrl());
            return Collections.emptyList();
        }
        double catWeight = page.getCategory() != null && page.getCategory().getWeight() != null ? page.getCategory().getWeight() : 0.0;
        List<Article> out = new ArrayList<>();
        for (org.jsoup.nodes.Element el : elements) {
            String title = page.getTitleSelector() != null && !page.getTitleSelector().isBlank()
                    ? el.select(page.getTitleSelector()).text() : "";
            String link = page.getLinkSelector() != null && !page.getLinkSelector().isBlank()
                    ? el.select(page.getLinkSelector()).attr("abs:href") : "";

            if (link.isBlank() || link.length() > MAX_LINK_LENGTH || !knownLinks.add(link)) continue;

            Article article = new Article();
            article.setTitle(title);
            article.setLink(link);
            article.setSource(page.getName());

            String description = el.text().replace(title, "").trim();
            if (description.isEmpty()) {
                description = doc.select("meta[name=description]").attr("content");
                if (description.isEmpty()) description = title;
            }
            if (description.length() > 300) description = description.substring(0, 297) + "...";
            article.setDescription(description);
            article.setBaseScore(page.getTrust() + catWeight);
            out.add(article);
        }
        return out;
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
