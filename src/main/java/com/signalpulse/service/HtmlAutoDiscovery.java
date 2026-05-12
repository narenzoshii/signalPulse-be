package com.signalpulse.service;

import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Heuristic article extractor — finds article listings on an arbitrary news
 * page without requiring the user to author CSS selectors.
 *
 * Algorithm:
 *  1. Strip non-content regions (header/footer/nav/aside/script/style).
 *  2. Collect every {@code <a href>} that looks like an article link
 *     (internal, plausible title length, real path).
 *  3. Group anchors by their "structural signature" — the tag-chain of the
 *     anchor's ancestors plus one stable class per level. Listings of
 *     articles almost always share an identical signature.
 *  4. Score each group on: repetition, URL diversity, title length,
 *     URL depth (article slugs are usually deeper than nav links).
 *  5. Return the best group.
 */
@Component
@Slf4j
public class HtmlAutoDiscovery {

    private static final Pattern STABLE_CLASS = Pattern.compile("^[a-z][a-z0-9_-]{2,}$");
    private static final int MIN_TITLE_LEN = 10;
    private static final int MAX_TITLE_LEN = 250;
    private static final int MIN_GROUP_SIZE = 3;
    private static final int PARENT_DEPTH = 5;

    public DiscoveryResult discover(Document doc, String baseUrl) {
        URI base;
        try {
            base = URI.create(baseUrl);
        } catch (Exception e) {
            return DiscoveryResult.empty();
        }
        String baseHost = base.getHost();
        if (baseHost == null) return DiscoveryResult.empty();

        Document working = doc.clone();
        working.select("header, footer, nav, aside, script, style, noscript, form, .header, .footer, .nav, .sidebar, .menu").remove();

        Map<String, List<Element>> groups = new LinkedHashMap<>();
        for (Element a : working.select("a[href]")) {
            String text = a.text().trim();
            if (text.length() < MIN_TITLE_LEN || text.length() > MAX_TITLE_LEN) continue;

            String href = a.absUrl("href");
            if (href.isBlank() || href.startsWith("javascript:") || href.startsWith("mailto:") || href.startsWith("#")) continue;

            URI hrefUri;
            try {
                hrefUri = URI.create(href);
            } catch (Exception e) {
                continue;
            }
            if (hrefUri.getHost() == null || !sameSite(baseHost, hrefUri.getHost())) continue;
            String path = hrefUri.getPath();
            if (path == null || path.length() < 4 || path.equals("/")) continue;

            groups.computeIfAbsent(signatureOf(a), k -> new ArrayList<>()).add(a);
        }

        Map.Entry<String, List<Element>> best = groups.entrySet().stream()
                .filter(e -> e.getValue().size() >= MIN_GROUP_SIZE)
                .max(Comparator.comparingDouble(e -> scoreGroup(e.getValue())))
                .orElse(null);
        if (best == null) {
            log.debug("Auto-discovery on {} found no group with ≥{} candidates", baseUrl, MIN_GROUP_SIZE);
            return DiscoveryResult.empty();
        }

        // De-dupe by link, preserve order
        Set<String> seen = new LinkedHashSet<>();
        List<DiscoveredArticle> articles = new ArrayList<>();
        for (Element a : best.getValue()) {
            String link = a.absUrl("href");
            if (!seen.add(link)) continue;
            articles.add(DiscoveredArticle.builder()
                    .title(a.text().trim())
                    .link(link)
                    .description(extractDescription(a))
                    .build());
        }
        return DiscoveryResult.builder()
                .discoveredSelector(best.getKey())
                .articles(articles)
                .build();
    }

    private boolean sameSite(String host1, String host2) {
        if (host1 == null || host2 == null) return false;
        if (host1.equalsIgnoreCase(host2)) return true;
        // Accept www.example.com vs example.com
        return host2.endsWith("." + host1) || host1.endsWith("." + host2);
    }

    private String signatureOf(Element anchor) {
        Deque<String> parts = new ArrayDeque<>();
        Element cur = anchor;
        for (int i = 0; i < PARENT_DEPTH && cur != null && !"body".equalsIgnoreCase(cur.tagName()); i++) {
            String tag = cur.tagName();
            String stable = Arrays.stream(cur.className().split("\\s+"))
                    .filter(c -> STABLE_CLASS.matcher(c).matches())
                    .findFirst().orElse("");
            parts.addFirst(stable.isEmpty() ? tag : tag + "." + stable);
            cur = cur.parent();
        }
        return String.join(" > ", parts);
    }

    private double scoreGroup(List<Element> group) {
        int count = group.size();
        double avgTextLen = group.stream().mapToInt(e -> e.text().length()).average().orElse(0);
        // Titles are typically 30–100 chars; penalise both extremes.
        double titleScore = avgTextLen <= 0 ? 0 : 1.0 - Math.abs(60 - Math.min(150, avgTextLen)) / 90.0;

        double avgDepth = group.stream()
                .mapToInt(e -> {
                    String p = e.absUrl("href");
                    try {
                        String path = URI.create(p).getPath();
                        return Math.min(6, path.split("/").length);
                    } catch (Exception ex) { return 0; }
                }).average().orElse(0);
        double depthScore = avgDepth / 6.0; // article slugs are usually depth ≥ 3

        long unique = group.stream().map(e -> e.absUrl("href")).distinct().count();
        double diversity = (double) unique / count;

        // log(count) keeps the score from being dominated solely by very large groups
        return Math.log(count + 1) * (1 + titleScore + depthScore + diversity);
    }

    private String extractDescription(Element link) {
        Element parent = link.parent();
        if (parent == null) return "";
        String allText = parent.text();
        if (allText.length() <= link.text().length() + 20) return "";
        String desc = allText.replace(link.text(), "").trim();
        return desc.length() > 300 ? desc.substring(0, 297) + "..." : desc;
    }

    @Value
    @Builder
    public static class DiscoveredArticle {
        String title;
        String link;
        String description;
    }

    @Value
    @Builder
    public static class DiscoveryResult {
        String discoveredSelector;
        List<DiscoveredArticle> articles;

        public static DiscoveryResult empty() {
            return DiscoveryResult.builder()
                    .discoveredSelector("")
                    .articles(List.of())
                    .build();
        }
    }
}
