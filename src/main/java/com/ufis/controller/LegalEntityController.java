package com.ufis.controller;

import com.ufis.domain.enums.LegalEntityState;
import com.ufis.domain.enums.LegalEntityType;
import com.ufis.dto.request.CreateLegalEntityRequest;
import com.ufis.dto.response.LegalEntityActionListEntryResponse;
import com.ufis.dto.response.LegalEntityLineageResponse;
import com.ufis.dto.response.LegalEntityResponse;
import com.ufis.dto.response.SearchLegalEntityResultResponse;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.service.ActionService;
import com.ufis.service.DtoMapper;
import com.ufis.service.LegalEntityService;
import datomic.Connection;
import com.ufis.service.lineage.LegalEntityLineageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/legal-entity")
public class LegalEntityController {
    private final LegalEntityService legalEntityService;
    private final LegalEntityLineageService legalEntityLineageService;
    private final ActionService actionService;
    private final LegalEntityRepository legalEntityRepository;
    private final DtoMapper dtoMapper;
    private final Connection connection;

    public LegalEntityController(LegalEntityService legalEntityService, LegalEntityLineageService legalEntityLineageService, ActionService actionService,
                                 LegalEntityRepository legalEntityRepository, DtoMapper dtoMapper, Connection connection) {
        this.legalEntityService = legalEntityService;
        this.legalEntityLineageService = legalEntityLineageService;
        this.actionService = actionService;
        this.legalEntityRepository = legalEntityRepository;
        this.dtoMapper = dtoMapper;
        this.connection = connection;
    }

    @GetMapping
    public List<SearchLegalEntityResultResponse> browse(@RequestParam(required = false) LegalEntityType type, @RequestParam(required = false) LegalEntityState state) {
        return legalEntityRepository.findByFilters(connection.db(), type, state)
                .stream()
                .map(dtoMapper::toSearchLegalEntityResultResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LegalEntityResponse create(@Valid @RequestBody CreateLegalEntityRequest request) {
        return legalEntityService.create(request);
    }

    @GetMapping("/{id}")
    public LegalEntityResponse getById(@PathVariable UUID id) {
        return legalEntityService.getById(id);
    }

    @GetMapping("/{id}/lineage")
    public LegalEntityLineageResponse getLineage(@PathVariable UUID id, @RequestParam(required = false) Instant validAt) {
        return legalEntityLineageService.getLineage(id, validAt);
    }

    @GetMapping("/{id}/actions")
    public List<LegalEntityActionListEntryResponse> getActions(@PathVariable UUID id) {
        return actionService.getLegalEntityActions(id);
    }
}