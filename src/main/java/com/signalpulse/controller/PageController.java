package com.signalpulse.controller;

import com.signalpulse.entity.HtmlPage;
import com.signalpulse.repository.HtmlPageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pages")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class PageController {
    private final HtmlPageRepository repository;

    @GetMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('OP_READ_ALL')")
    public List<HtmlPage> getAll() { return repository.findAll(); }


    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('OP_WRITE_SOURCES')")
    public HtmlPage save(@RequestBody HtmlPage page) { return repository.save(page); }


    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('OP_WRITE_SOURCES')")
    public void delete(@PathVariable Long id) { repository.deleteById(id); }

}
