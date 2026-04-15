package com.ufis.controller;

import com.ufis.domain.enums.SecurityState;
import com.ufis.domain.enums.SecurityType;
import com.ufis.dto.request.CreateSecurityRequest;
import com.ufis.dto.response.SearchSecurityResultResponse;
import com.ufis.dto.response.SecurityActionListEntryResponse;
import com.ufis.dto.response.SecurityLineageResponse;
import com.ufis.dto.response.SecurityResponse;
import com.ufis.repository.SecurityRepository;
import com.ufis.service.ActionService;
import com.ufis.service.DtoMapper;
import com.ufis.service.SecurityService;
import datomic.Connection;
import com.ufis.service.lineage.AuditLineageService;
import com.ufis.service.lineage.SecurityLineageService;
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
@RequestMapping("/security")
public class SecurityController {
    private final SecurityService securityService;
    private final SecurityLineageService securityLineageService;
    private final AuditLineageService auditLineageService;
    private final ActionService actionService;
    private final SecurityRepository securityRepository;
    private final DtoMapper dtoMapper;
    private final Connection connection;

    public SecurityController(SecurityService securityService, SecurityLineageService securityLineageService, AuditLineageService auditLineageService,
                              ActionService actionService, SecurityRepository securityRepository, DtoMapper dtoMapper, Connection connection) {
        this.securityService = securityService;
        this.securityLineageService = securityLineageService;
        this.auditLineageService = auditLineageService;
        this.actionService = actionService;
        this.securityRepository = securityRepository;
        this.dtoMapper = dtoMapper;
        this.connection = connection;
    }

    @GetMapping
    public List<SearchSecurityResultResponse> browse(@RequestParam(required = false) SecurityType type, @RequestParam(required = false) SecurityState state) {
        return securityRepository.findByFilters(connection.db(), type, state)
                .stream()
                .map(dtoMapper::toSearchSecurityResultResponse)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SecurityResponse create(@Valid @RequestBody CreateSecurityRequest request) {
        return securityService.create(request);
    }

    @GetMapping("/{id}")
    public SecurityResponse getById(@PathVariable UUID id) {
        return securityService.getById(id);
    }

    @GetMapping("/{id}/lineage")
    public SecurityLineageResponse getLineage(@PathVariable UUID id, @RequestParam(required = false) Instant validAt) {
        return securityLineageService.getLineage(id, validAt);
    }

    @GetMapping("/{id}/lineage/audit")
    public SecurityLineageResponse getAuditLineage(@PathVariable UUID id, @RequestParam Instant validAt, @RequestParam Instant knownAt) {
        return auditLineageService.getSecurityAuditLineage(id, validAt, knownAt);
    }

    @GetMapping("/{id}/actions")
    public List<SecurityActionListEntryResponse> getActions(@PathVariable UUID id) {
        return actionService.getSecurityActions(id);
    }
}