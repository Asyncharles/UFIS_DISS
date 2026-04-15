package com.ufis.controller;

import com.ufis.dto.response.DummyDataSeedResponse;
import com.ufis.service.DummyDataSeedService;
import com.ufis.simulator.DataSimulator;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final DummyDataSeedService dummyDataSeedService;

    public AdminController(DummyDataSeedService dummyDataSeedService) {
        this.dummyDataSeedService = dummyDataSeedService;
    }

    @PostMapping("/seed-dummy-data")
    @ResponseStatus(HttpStatus.CREATED)
    public DummyDataSeedResponse seedDummyData(
            @RequestParam(defaultValue = "SMALL") DataSimulator.Tier tier
    ) throws Exception {
        return dummyDataSeedService.seed(tier);
    }
}