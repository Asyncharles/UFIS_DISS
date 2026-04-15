package com.ufis.controller;

import com.ufis.dto.response.SearchResponse;
import com.ufis.service.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SearchController {
    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/search")
    public SearchResponse search(@RequestParam String q) {
        return searchService.search(q);
    }
}