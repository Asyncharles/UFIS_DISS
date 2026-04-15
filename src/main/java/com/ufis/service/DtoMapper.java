package com.ufis.service;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.domain.enums.LegalEntityRole;
import com.ufis.domain.enums.LegalEntityState;
import com.ufis.domain.enums.LegalEntityType;
import com.ufis.domain.enums.SecurityRole;
import com.ufis.domain.enums.SecurityState;
import com.ufis.domain.enums.SecurityType;
import com.ufis.dto.response.CorporateActionRecordResponse;
import com.ufis.dto.response.SearchCorporateActionResultResponse;
import com.ufis.dto.response.SearchLegalEntityResultResponse;
import com.ufis.dto.response.SearchSecurityResultResponse;
import com.ufis.dto.response.IssuerRefResponse;
import com.ufis.dto.response.IssuerUpdateResponse;
import com.ufis.dto.response.LegalEntityActionListEntryResponse;
import com.ufis.dto.response.LegalEntityResponse;
import com.ufis.dto.response.LineageRecordResponse;
import com.ufis.dto.response.SecurityActionListEntryResponse;
import com.ufis.dto.response.SecurityResponse;
import com.ufis.util.DateUtils;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

@Component
public class DtoMapper {
    public LegalEntityResponse toLegalEntityResponse(Map<String, Object> entity) {
        return new LegalEntityResponse(
                (UUID) entity.get("id"),
                (String) entity.get("name"),
                (LegalEntityType) entity.get("type"),
                (LegalEntityState) entity.get("state"),
                (Boolean) entity.get("active"),
                DateUtils.toInstant((Date) entity.get("foundedDate"))
        );
    }

    @SuppressWarnings("unchecked")
    public SecurityResponse toSecurityResponse(Map<String, Object> security) {
        Map<String, Object> issuer = (Map<String, Object>) security.get("issuer");
        return new SecurityResponse(
                (UUID) security.get("id"),
                (String) security.get("name"),
                (String) security.get("isin"),
                (SecurityType) security.get("type"),
                (SecurityState) security.get("state"),
                (Boolean) security.get("active"),
                DateUtils.toInstant((Date) security.get("issueDate")),
                DateUtils.toInstant((Date) security.get("maturityDate")),
                issuer == null ? null : new IssuerRefResponse((UUID) issuer.get("id"), (String) issuer.get("name"))
        );
    }

    public CorporateActionRecordResponse toCorporateActionRecordResponse(Map<String, Object> action) {
        return new CorporateActionRecordResponse(
                (UUID) action.get("id"),
                (CorporateActionType) action.get("type"),
                DateUtils.toInstant((Date) action.get("validDate")),
                (String) action.get("description"),
                (String) action.get("splitRatio")
        );
    }

    @SuppressWarnings("unchecked")
    public SearchSecurityResultResponse toSearchSecurityResultResponse(Map<String, Object> security) {
        Map<String, Object> issuer = (Map<String, Object>) security.get("issuer");
        return new SearchSecurityResultResponse(
                (UUID) security.get("id"),
                (String) security.get("name"),
                (String) security.get("isin"),
                (SecurityType) security.get("type"),
                (SecurityState) security.get("state"),
                (Boolean) security.get("active"),
                issuer == null ? null : (UUID) issuer.get("id"),
                issuer == null ? null : (String) issuer.get("name")
        );
    }

    public SearchLegalEntityResultResponse toSearchLegalEntityResultResponse(Map<String, Object> entity) {
        return new SearchLegalEntityResultResponse(
                (UUID) entity.get("id"),
                (String) entity.get("name"),
                (LegalEntityType) entity.get("type"),
                (LegalEntityState) entity.get("state"),
                (Boolean) entity.get("active")
        );
    }

    public SearchCorporateActionResultResponse toSearchCorporateActionResultResponse(Map<String, Object> action) {
        CorporateActionRecordResponse record = toCorporateActionRecordResponse(action);
        return new SearchCorporateActionResultResponse(
                record.id(),
                record.type(),
                record.validDate(),
                record.description()
        );
    }

    public LineageRecordResponse toSecurityLineageRecordResponse(UUID lineageId, UUID actionId,
                                                                 UUID parentSecurityId, UUID childSecurityId) {
        return new LineageRecordResponse(
                lineageId,
                actionId,
                parentSecurityId,
                childSecurityId,
                null,
                null
        );
    }

    public LineageRecordResponse toLegalEntityLineageRecordResponse(UUID lineageId, UUID actionId,
                                                                    UUID parentEntityId, UUID childEntityId) {
        return new LineageRecordResponse(
                lineageId,
                actionId,
                null,
                null,
                parentEntityId,
                childEntityId
        );
    }

    public IssuerUpdateResponse toIssuerUpdateResponse(UUID securityId, UUID oldIssuerId, UUID newIssuerId) {
        return new IssuerUpdateResponse(securityId, oldIssuerId, newIssuerId);
    }

    public SecurityActionListEntryResponse toSecurityActionListEntryResponse(Map<String, Object> action,
                                                                             SecurityRole role) {
        return new SecurityActionListEntryResponse(toCorporateActionRecordResponse(action), role);
    }

    public LegalEntityActionListEntryResponse toLegalEntityActionListEntryResponse(Map<String, Object> action,
                                                                                   LegalEntityRole role) {
        return new LegalEntityActionListEntryResponse(toCorporateActionRecordResponse(action), role);
    }
}
