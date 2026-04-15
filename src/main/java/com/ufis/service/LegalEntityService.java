package com.ufis.service;

import com.ufis.dto.request.CreateLegalEntityRequest;
import com.ufis.dto.response.LegalEntityResponse;
import com.ufis.repository.LegalEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class LegalEntityService {
    private final LegalEntityRepository legalEntityRepository;
    private final DtoMapper dtoMapper;

    public LegalEntityResponse create(CreateLegalEntityRequest request) {
        try {
            UUID id = legalEntityRepository.create(request.name(), request.type(), Date.from(request.foundedDate()));

            return getById(id);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to create legal entity name={}", request.name(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create legal entity", ex);
        }
    }

    public LegalEntityResponse getById(UUID id) {
        Map<String, Object> entity = legalEntityRepository.findById(id);

        if (entity == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Legal entity not found");
        }

        return dtoMapper.toLegalEntityResponse(entity);
    }
}
