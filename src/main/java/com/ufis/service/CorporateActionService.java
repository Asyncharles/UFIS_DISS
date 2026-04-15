package com.ufis.service;

import com.ufis.dto.response.CorporateActionRecordResponse;
import com.ufis.repository.CorporateActionRepository;
import datomic.Connection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CorporateActionService {
    private final CorporateActionRepository corporateActionRepository;
    private final DtoMapper dtoMapper;
    private final Connection connection;

    public List<CorporateActionRecordResponse> getRecent(int limit) {
        return corporateActionRepository.findAllSortedByDateDesc(connection.db(), limit)
                .stream()
                .map(dtoMapper::toCorporateActionRecordResponse)
                .toList();
    }

    public CorporateActionRecordResponse getById(UUID id) {
        Map<String, Object> action = corporateActionRepository.findById(id);

        if (action == null) {
            log.warn("Corporate action not found id={}", id);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Corporate action not found");
        }

        return dtoMapper.toCorporateActionRecordResponse(action);
    }
}
