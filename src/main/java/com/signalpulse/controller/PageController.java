package com.signalpulse.controller;

import com.signalpulse.dto.HtmlPageRequest;
import com.signalpulse.dto.PageResponse;
import com.signalpulse.entity.Category;
import com.signalpulse.entity.HtmlPage;
import com.signalpulse.repository.CategoryRepository;
import com.signalpulse.repository.HtmlPageRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pages")
@RequiredArgsConstructor
public class PageController {
    private static final int MAX_PAGE_SIZE = 200;

    private final HtmlPageRepository repository;
    private final CategoryRepository categoryRepository;

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
        HtmlPage page = body.getId() == null
                ? new HtmlPage()
                : repository.findById(body.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Page not found: " + body.getId()));
        page.setName(body.getName());
        page.setUrl(body.getUrl());
        page.setDescription(body.getDescription());
        page.setType(body.getType());
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

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) return null;
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new EntityNotFoundException("Category not found: " + categoryId));
    }
}
