package com.ufis.service;

import com.ufis.dto.response.SearchCorporateActionResultResponse;
import com.ufis.dto.response.SearchLegalEntityResultResponse;
import com.ufis.dto.response.SearchResponse;
import com.ufis.dto.response.SearchSecurityResultResponse;
import com.ufis.repository.CorporateActionRepository;
import com.ufis.repository.LegalEntityRepository;
import com.ufis.repository.SecurityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SearchService {
    private static final int GROUP_LIMIT = 10;

    private static final Comparator<SearchHit<?>> HIT_ORDER = Comparator.<SearchHit<?>, Integer>comparing(SearchHit::score)
                    .reversed()
                    .thenComparing(SearchHit::sortKey, String.CASE_INSENSITIVE_ORDER);

    private final SecurityRepository securityRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final CorporateActionRepository corporateActionRepository;
    private final DtoMapper dtoMapper;

    public SearchResponse search(String rawQuery) {
        String query = normalize(rawQuery);

        if (query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query must not be blank");
        }

        UUID exactUuid = tryParseUuid(query);

        return new SearchResponse(
                searchSecurities(query, exactUuid),
                searchLegalEntities(query, exactUuid),
                searchCorporateActions(query, exactUuid)
        );
    }

    private List<SearchSecurityResultResponse> searchSecurities(String query, UUID exactUuid) {
        return securityRepository.findAll().stream()
                .map(security -> scoreSecurity(security, query, exactUuid))
                .filter(Objects::nonNull)
                .sorted(HIT_ORDER)
                .limit(GROUP_LIMIT)
                .map(hit -> dtoMapper.toSearchSecurityResultResponse(hit.payload()))
                .toList();
    }

    private List<SearchLegalEntityResultResponse> searchLegalEntities(String query, UUID exactUuid) {
        return legalEntityRepository.findAll().stream()
                .map(entity -> scoreLegalEntity(entity, query, exactUuid))
                .filter(Objects::nonNull)
                .sorted(HIT_ORDER)
                .limit(GROUP_LIMIT)
                .map(hit -> dtoMapper.toSearchLegalEntityResultResponse(hit.payload()))
                .toList();
    }

    private List<SearchCorporateActionResultResponse> searchCorporateActions(String query, UUID exactUuid) {
        return corporateActionRepository.findAll().stream()
                .map(action -> scoreCorporateAction(action, query, exactUuid))
                .filter(Objects::nonNull)
                .sorted(HIT_ORDER)
                .limit(GROUP_LIMIT)
                .map(hit -> dtoMapper.toSearchCorporateActionResultResponse(hit.payload()))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private SearchHit<Map<String, Object>> scoreSecurity(Map<String, Object> security, String query, UUID exactUuid) {
        Map<String, Object> issuer = (Map<String, Object>) security.get("issuer");
        int score = maxScore(
                uuidScore(query, exactUuid, (UUID) security.get("id")),
                textScore(query, (String) security.get("name")),
                textScore(query, (String) security.get("isin")),
                textScore(query, issuer == null ? null : (String) issuer.get("name")) - 100,
                enumScore(query, security.get("type")),
                enumScore(query, security.get("state"))
        );

        if (score < 0) {
            return null;
        }

        return new SearchHit<>(security, score, (String) security.get("name"));
    }

    private SearchHit<Map<String, Object>> scoreLegalEntity(Map<String, Object> entity, String query, UUID exactUuid) {
        int score = maxScore(
                uuidScore(query, exactUuid, (UUID) entity.get("id")),
                textScore(query, (String) entity.get("name")),
                enumScore(query, entity.get("type")),
                enumScore(query, entity.get("state"))
        );

        if (score < 0) {
            return null;
        }

        return new SearchHit<>(entity, score, (String) entity.get("name"));
    }

    private SearchHit<Map<String, Object>> scoreCorporateAction(Map<String, Object> action, String query, UUID exactUuid) {
        int score = maxScore(
                uuidScore(query, exactUuid, (UUID) action.get("id")),
                textScore(query, (String) action.get("description")),
                enumScore(query, action.get("type"))
        );

        if (score < 0) {
            return null;
        }

        String sortKey = (String) action.get("description");

        return new SearchHit<>(action, score, sortKey == null ? action.get("id").toString() : sortKey);
    }

    private int uuidScore(String query, UUID exactUuid, UUID candidateId) {
        if (exactUuid == null || candidateId == null) {
            return -1;
        }

        return candidateId.equals(exactUuid) ? 1_000 : -1;
    }

    private int enumScore(String query, Object candidate) {
        return candidate == null ? -1 : textScore(query, candidate.toString());
    }

    private int textScore(String query, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return -1;
        }

        String normalizedCandidate = normalize(candidate);

        if (normalizedCandidate.equals(query)) {
            return 900;
        }

        if (normalizedCandidate.startsWith(query)) {
            return 700;
        }

        if (normalizedCandidate.contains(query)) {
            return 500;
        }

        return -1;
    }

    private int maxScore(int... scores) {
        int max = -1;

        for (int score : scores) {
            max = Math.max(max, score);
        }

        return max;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private record SearchHit<T>(T payload, int score, String sortKey) {}
}