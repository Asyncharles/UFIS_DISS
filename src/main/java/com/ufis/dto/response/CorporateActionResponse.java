package com.ufis.dto.response;

import java.util.List;

public record CorporateActionResponse(
        CorporateActionRecordResponse corporateAction,
        List<LegalEntityResponse> createdEntities,
        List<LegalEntityResponse> terminatedEntities,
        List<SecurityResponse> createdSecurities,
        List<SecurityResponse> terminatedSecurities,
        List<LineageRecordResponse> lineageRecords,
        List<IssuerUpdateResponse> issuerUpdates
) {

    public static Builder builder(CorporateActionRecordResponse corporateAction) {
        return new Builder(corporateAction);
    }

    public static final class Builder {
        private final CorporateActionRecordResponse corporateAction;
        private List<LegalEntityResponse> createdEntities = List.of();
        private List<LegalEntityResponse> terminatedEntities = List.of();
        private List<SecurityResponse> createdSecurities = List.of();
        private List<SecurityResponse> terminatedSecurities = List.of();
        private List<LineageRecordResponse> lineageRecords = List.of();
        private List<IssuerUpdateResponse> issuerUpdates = List.of();

        private Builder(CorporateActionRecordResponse corporateAction) {
            this.corporateAction = corporateAction;
        }

        public Builder createdEntities(List<LegalEntityResponse> createdEntities) {
            this.createdEntities = createdEntities;
            return this;
        }

        public Builder terminatedEntities(List<LegalEntityResponse> terminatedEntities) {
            this.terminatedEntities = terminatedEntities;
            return this;
        }

        public Builder createdSecurities(List<SecurityResponse> createdSecurities) {
            this.createdSecurities = createdSecurities;
            return this;
        }

        public Builder terminatedSecurities(List<SecurityResponse> terminatedSecurities) {
            this.terminatedSecurities = terminatedSecurities;
            return this;
        }

        public Builder lineageRecords(List<LineageRecordResponse> lineageRecords) {
            this.lineageRecords = lineageRecords;
            return this;
        }

        public Builder issuerUpdates(List<IssuerUpdateResponse> issuerUpdates) {
            this.issuerUpdates = issuerUpdates;
            return this;
        }

        public CorporateActionResponse build() {
            return new CorporateActionResponse(
                    corporateAction,
                    createdEntities,
                    terminatedEntities,
                    createdSecurities,
                    terminatedSecurities,
                    lineageRecords,
                    issuerUpdates
            );
        }
    }
}
