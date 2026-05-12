package com.signalpulse.service;

import com.signalpulse.repository.HtmlPageRepository;
import com.signalpulse.repository.RssFeedRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Persists per-source health after each scan attempt and auto-disables sources
 * that have failed too many times in a row. Methods open their own short
 * transactions so they can be called safely from the scan executor threads.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SourceHealthService {

    private static final int DEFAULT_AUTO_DISABLE_THRESHOLD = 10;

    private final RssFeedRepository rssFeedRepository;
    private final HtmlPageRepository htmlPageRepository;
    private final ConfigService configService;

    @Transactional
    public void recordRssSuccess(Long feedId, int articleCount) {
        rssFeedRepository.findById(feedId).ifPresent(feed -> {
            feed.setLastScanAt(LocalDateTime.now());
            feed.setLastScanStatus("SUCCESS");
            feed.setLastScanError(null);
            feed.setConsecutiveFailures(0);
            feed.setLastArticleCount(articleCount);
            rssFeedRepository.save(feed);
        });
    }

    @Transactional
    public void recordRssFailure(Long feedId, String error) {
        rssFeedRepository.findById(feedId).ifPresent(feed -> {
            int failures = feed.getConsecutiveFailures() + 1;
            feed.setLastScanAt(LocalDateTime.now());
            feed.setLastScanStatus("FAILURE");
            feed.setLastScanError(truncate(error, 500));
            feed.setConsecutiveFailures(failures);

            int threshold = autoDisableThreshold();
            if (failures >= threshold && feed.isEnabled()) {
                feed.setEnabled(false);
                log.warn("Auto-disabled RSS feed '{}' after {} consecutive failures. Last error: {}",
                        feed.getName(), failures, truncate(error, 200));
            }
            rssFeedRepository.save(feed);
        });
    }

    @Transactional
    public void recordHtmlSuccess(Long pageId, int articleCount) {
        htmlPageRepository.findById(pageId).ifPresent(page -> {
            page.setLastScanAt(LocalDateTime.now());
            page.setLastScanStatus("SUCCESS");
            page.setLastScanError(null);
            page.setConsecutiveFailures(0);
            page.setLastArticleCount(articleCount);
            htmlPageRepository.save(page);
        });
    }

    @Transactional
    public void recordHtmlFailure(Long pageId, String error) {
        htmlPageRepository.findById(pageId).ifPresent(page -> {
            int failures = page.getConsecutiveFailures() + 1;
            page.setLastScanAt(LocalDateTime.now());
            page.setLastScanStatus("FAILURE");
            page.setLastScanError(truncate(error, 500));
            page.setConsecutiveFailures(failures);

            int threshold = autoDisableThreshold();
            if (failures >= threshold && page.isEnabled()) {
                page.setEnabled(false);
                log.warn("Auto-disabled HTML page '{}' after {} consecutive failures. Last error: {}",
                        page.getName(), failures, truncate(error, 200));
            }
            htmlPageRepository.save(page);
        });
    }

    private int autoDisableThreshold() {
        try {
            return Integer.parseInt(configService.getConfig("AUTO_DISABLE_AFTER_FAILURES")
                    .orElse(String.valueOf(DEFAULT_AUTO_DISABLE_THRESHOLD)));
        } catch (NumberFormatException e) {
            return DEFAULT_AUTO_DISABLE_THRESHOLD;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
