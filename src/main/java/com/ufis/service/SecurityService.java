package com.ufis.service;

import com.ufis.domain.enums.SecurityType;
import com.ufis.dto.request.CreateSecurityRequest;
import com.ufis.dto.response.SecurityResponse;
import com.ufis.repository.SecurityRepository;
import com.ufis.validation.CorporateActionValidator;
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
public class SecurityService {
    private final SecurityRepository securityRepository;
    private final DtoMapper dtoMapper;
    private final CorporateActionValidator corporateActionValidator;

    public SecurityResponse create(CreateSecurityRequest request) {
        validateSecurityRequest(request);

        try {
            UUID id = securityRepository.create(
                    request.name(),
                    request.type(),
                    request.issuerId(),
                    Date.from(request.issueDate()),
                    request.isin(),
                    request.maturityDate() == null ? null : Date.from(request.maturityDate())
            );

            return getById(id);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to create security name={}", request.name(), ex);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to create security", ex);
        }
    }

    public SecurityResponse getById(UUID id) {
        Map<String, Object> security = securityRepository.findById(id);

        if (security == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Security not found");
        }

        return dtoMapper.toSecurityResponse(security);
    }

    private void validateSecurityRequest(CreateSecurityRequest request) {
        corporateActionValidator.validateSecurityFields(request.type(), request.maturityDate());

        try {
            corporateActionValidator.requireActiveLegalEntity(request.issuerId());
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Issuer not found", ex);
            }
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Issuer is inactive", ex);
            }
            throw ex;
        }
    }
}
