package com.signalpulse.controller;

import com.signalpulse.dto.HtmlPagePreviewRequest;
import com.signalpulse.dto.HtmlPageRequest;
import com.signalpulse.dto.PageResponse;
import com.signalpulse.entity.Category;
import com.signalpulse.entity.HtmlPage;
import com.signalpulse.repository.CategoryRepository;
import com.signalpulse.repository.HtmlPageRepository;
import com.signalpulse.service.HtmlAutoDiscovery;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pages")
@RequiredArgsConstructor
public class PageController {
    private static final int MAX_PAGE_SIZE = 200;
    private static final int PREVIEW_LIMIT = 10;

    private final HtmlPageRepository repository;
    private final CategoryRepository categoryRepository;
    private final HtmlAutoDiscovery autoDiscovery;

    @GetMapping
    @PreAuthorize("hasAuthority('OP_READ_ALL')")
    public PageResponse<HtmlPage> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "name") String sort) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(size, MAX_PAGE_SIZE), Sort.by(sort).ascending());
        return PageResponse.from(repository.findAll(pageable));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('OP_WRITE_SOURCES')")
    public HtmlPage save(@Valid @RequestBody HtmlPageRequest body) {
        // Manual mode needs a listSelector — Auto mode does not.
        if ("manual".equalsIgnoreCase(body.getDiscoveryMode())
                && (body.getListSelector() == null || body.getListSelector().isBlank())) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "listSelector is required in manual mode");
        }

        HtmlPage page = body.getId() == null
                ? new HtmlPage()
                : repository.findById(body.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Page not found: " + body.getId()));
        page.setName(body.getName());
        page.setUrl(body.getUrl());
        page.setDescription(body.getDescription());
        page.setType(body.getType());
        page.setDiscoveryMode(body.getDiscoveryMode());
        page.setListSelector(body.getListSelector());
        page.setTitleSelector(body.getTitleSelector());
        page.setLinkSelector(body.getLinkSelector());
        page.setDateSelector(body.getDateSelector());
        page.setTrust(body.getTrust());
        page.setEnabled(body.isEnabled());
        page.setCategory(resolveCategory(body.getCategoryId()));
        return repository.save(page);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('OP_WRITE_SOURCES')")
    public void delete(@PathVariable Long id) {
        repository.deleteById(id);
    }

    /**
     * Test what auto-discovery (or a manual selector) would extract from a
     * given URL, *without* saving the page. Used by the "Test Source" button.
     */
    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('OP_WRITE_SOURCES')")
    public Map<String, Object> preview(@Valid @RequestBody HtmlPagePreviewRequest body) {
        try {
            Document doc = Jsoup.connect(body.getUrl())
                    .userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
                    .timeout(15000)
                    .followRedirects(true)
                    .get();

            // If the page advertises an RSS/Atom feed, surface it. RSS scrapes
            // are far more reliable than DOM scraping.
            List<Map<String, String>> rssLinks = findFeedLinks(doc);

            boolean isAuto = body.getDiscoveryMode() == null || "auto".equalsIgnoreCase(body.getDiscoveryMode());
            if (isAuto) {
                HtmlAutoDiscovery.DiscoveryResult r = autoDiscovery.discover(doc, body.getUrl());
                List<Map<String, String>> items = r.getArticles().stream()
                        .limit(PREVIEW_LIMIT)
                        .map(a -> Map.of(
                                "title", nonNull(a.getTitle()),
                                "link", nonNull(a.getLink())))
                        .toList();
                Map<String, Object> response = new java.util.LinkedHashMap<>();
                response.put("mode", "auto");
                response.put("discoveredSelector", r.getDiscoveredSelector());
                response.put("totalFound", r.getArticles().size());
                response.put("items", items);
                response.put("rssLinks", rssLinks);
                return response;
            } else {
                if (body.getListSelector() == null || body.getListSelector().isBlank()) {
                    throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                            "listSelector is required for manual preview");
                }
                Elements rows = doc.select(body.getListSelector());
                List<Map<String, String>> items = new ArrayList<>();
                for (int i = 0; i < rows.size() && items.size() < PREVIEW_LIMIT; i++) {
                    var el = rows.get(i);
                    String title = body.getTitleSelector() != null && !body.getTitleSelector().isBlank()
                            ? el.select(body.getTitleSelector()).text() : el.text();
                    String link = body.getLinkSelector() != null && !body.getLinkSelector().isBlank()
                            ? el.select(body.getLinkSelector()).attr("abs:href") : "";
                    if (link.isBlank()) continue;
                    items.add(Map.of("title", nonNull(title), "link", link));
                }
                Map<String, Object> response = new java.util.LinkedHashMap<>();
                response.put("mode", "manual");
                response.put("totalFound", rows.size());
                response.put("items", items);
                response.put("rssLinks", rssLinks);
                return response;
            }
        } catch (Exception e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,
                    "Failed to fetch " + body.getUrl() + ": " + e.getMessage());
        }
    }

    /** Detect RSS/Atom links advertised by the page so the UI can suggest using a feed. */
    private List<Map<String, String>> findFeedLinks(Document doc) {
        List<Map<String, String>> out = new ArrayList<>();
        Elements links = doc.select("link[rel=alternate][type=application/rss+xml], link[rel=alternate][type=application/atom+xml]");
        for (Element l : links) {
            String href = l.absUrl("href");
            if (href.isBlank()) continue;
            out.add(Map.of(
                    "title", nonNull(l.attr("title")),
                    "type", nonNull(l.attr("type")),
                    "url", href));
        }
        return out;
    }

    private static String nonNull(String s) { return s == null ? "" : s; }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found: " + categoryId));
    }
}
