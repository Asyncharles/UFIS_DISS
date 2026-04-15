package com.ufis.repository;

import datomic.Util;

import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public final class DatomicSchema {

    private DatomicSchema() {}

    private static final Map<Object, Object> LEGAL_ENTITY_ID = Util.map(
            ":db/ident", ":legal-entity/id",
            ":db/valueType", ":db.type/uuid",
            ":db/cardinality", ":db.cardinality/one",
            ":db/unique", ":db.unique/identity",
            ":db/doc", "UUID, unique identifier for a legal entity"
    );

    private static final Map<Object, Object> LEGAL_ENTITY_NAME = Util.map(
            ":db/ident", ":legal-entity/name",
            ":db/valueType", ":db.type/string",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Current name of the legal entity"
    );

    private static final Map<Object, Object> LEGAL_ENTITY_TYPE = Util.map(
            ":db/ident", ":legal-entity/type",
            ":db/valueType", ":db.type/ref",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Entity type enum: COMPANY, FUND, SPV, GOVERNMENT, SUPRANATIONAL"
    );

    private static final Map<Object, Object> LEGAL_ENTITY_STATE = Util.map(
            ":db/ident", ":legal-entity/state",
            ":db/valueType", ":db.type/ref",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Lifecycle state: ACTIVE, MERGED, ACQUIRED"
    );

    private static final Map<Object, Object> LEGAL_ENTITY_ACTIVE = Util.map(
            ":db/ident", ":legal-entity/active",
            ":db/valueType", ":db.type/boolean",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Convenience flag: true iff state = ACTIVE"
    );

    private static final Map<Object, Object> LEGAL_ENTITY_FOUNDED_DATE = Util.map(
            ":db/ident", ":legal-entity/founded-date",
            ":db/valueType", ":db.type/instant",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Date the entity was founded/incorporated"
    );

    private static final Map<Object, Object> SECURITY_ID = Util.map(
            ":db/ident", ":security/id",
            ":db/valueType", ":db.type/uuid",
            ":db/cardinality", ":db.cardinality/one",
            ":db/unique", ":db.unique/identity",
            ":db/doc", "UUID, unique identifier for a security"
    );

    private static final Map<Object, Object> SECURITY_NAME = Util.map(
            ":db/ident", ":security/name",
            ":db/valueType", ":db.type/string",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Display name of the security"
    );

    private static final Map<Object, Object> SECURITY_ISIN = Util.map(
            ":db/ident", ":security/isin",
            ":db/valueType", ":db.type/string",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Optional external ISIN identifier"
    );

    private static final Map<Object, Object> SECURITY_TYPE = Util.map(
            ":db/ident", ":security/type",
            ":db/valueType", ":db.type/ref",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Security type: EQUITY, BOND"
    );

    private static final Map<Object, Object> SECURITY_STATE = Util.map(
            ":db/ident", ":security/state",
            ":db/valueType", ":db.type/ref",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Lifecycle state: ACTIVE, MERGED, SPLIT, REDEEMED"
    );

    private static final Map<Object, Object> SECURITY_ISSUER = Util.map(
            ":db/ident", ":security/issuer",
            ":db/valueType", ":db.type/ref",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Ref to the issuing LegalEntity"
    );

    private static final Map<Object, Object> SECURITY_ISSUE_DATE = Util.map(
            ":db/ident", ":security/issue-date",
            ":db/valueType", ":db.type/instant",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Date the security was issued"
    );

    private static final Map<Object, Object> SECURITY_MATURITY_DATE = Util.map(
            ":db/ident", ":security/maturity-date",
            ":db/valueType", ":db.type/instant",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Maturity date (required for BOND, null for EQUITY)"
    );

    private static final Map<Object, Object> SECURITY_ACTIVE = Util.map(
            ":db/ident", ":security/active",
            ":db/valueType", ":db.type/boolean",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Convenience flag: true iff state = ACTIVE"
    );

    private static final Map<Object, Object> CORPORATE_ACTION_ID = Util.map(
            ":db/ident", ":corporate-action/id",
            ":db/valueType", ":db.type/uuid",
            ":db/cardinality", ":db.cardinality/one",
            ":db/unique", ":db.unique/identity",
            ":db/doc", "UUID, unique identifier for a corporate action"
    );

    private static final Map<Object, Object> CORPORATE_ACTION_TYPE = Util.map(
            ":db/ident", ":corporate-action/type",
            ":db/valueType", ":db.type/ref",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Action type enum"
    );

    private static final Map<Object, Object> CORPORATE_ACTION_VALID_DATE = Util.map(
            ":db/ident", ":corporate-action/valid-date",
            ":db/valueType", ":db.type/instant",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "When the action occurred in the real world (valid time)"
    );

    private static final Map<Object, Object> CORPORATE_ACTION_DESCRIPTION = Util.map(
            ":db/ident", ":corporate-action/description",
            ":db/valueType", ":db.type/string",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Human-readable description"
    );

    private static final Map<Object, Object> CORPORATE_ACTION_SPLIT_RATIO = Util.map(
            ":db/ident", ":corporate-action/split-ratio",
            ":db/valueType", ":db.type/string",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Split ratio string (STOCK_SPLIT only), e.g. '2:1'"
    );

    private static final Map<Object, Object> SECURITY_LINEAGE_ID = Util.map(
            ":db/ident", ":security-lineage/id",
            ":db/valueType", ":db.type/uuid",
            ":db/cardinality", ":db.cardinality/one",
            ":db/unique", ":db.unique/identity",
            ":db/doc", "UUID for this lineage record"
    );

    private static final Map<Object, Object> SECURITY_LINEAGE_PARENT = Util.map(
            ":db/ident", ":security-lineage/parent-security",
            ":db/valueType", ":db.type/ref",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Ref to the parent/source security"
    );

    private static final Map<Object, Object> SECURITY_LINEAGE_CHILD = Util.map(
            ":db/ident", ":security-lineage/child-security",
            ":db/valueType", ":db.type/ref",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Ref to the child/result security (nullable for terminal events)"
    );

    private static final Map<Object, Object> SECURITY_LINEAGE_ACTION = Util.map(
            ":db/ident", ":security-lineage/action",
            ":db/valueType", ":db.type/ref",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Ref to the corporate action that created this edge"
    );

    private static final Map<Object, Object> LEGAL_ENTITY_LINEAGE_ID = Util.map(
            ":db/ident", ":legal-entity-lineage/id",
            ":db/valueType", ":db.type/uuid",
            ":db/cardinality", ":db.cardinality/one",
            ":db/unique", ":db.unique/identity",
            ":db/doc", "UUID for this lineage record"
    );

    private static final Map<Object, Object> LEGAL_ENTITY_LINEAGE_PARENT = Util.map(
            ":db/ident", ":legal-entity-lineage/parent-entity",
            ":db/valueType", ":db.type/ref",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Ref to the parent/source legal entity"
    );

    private static final Map<Object, Object> LEGAL_ENTITY_LINEAGE_CHILD = Util.map(
            ":db/ident", ":legal-entity-lineage/child-entity",
            ":db/valueType", ":db.type/ref",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Ref to the child/result legal entity (nullable for dissolutions)"
    );

    private static final Map<Object, Object> LEGAL_ENTITY_LINEAGE_ACTION = Util.map(
            ":db/ident", ":legal-entity-lineage/action",
            ":db/valueType", ":db.type/ref",
            ":db/cardinality", ":db.cardinality/one",
            ":db/doc", "Ref to the corporate action that created this edge"
    );

    private static final Map<Object, Object> ENTITY_TYPE_COMPANY = Util.map(":db/ident", ":legal-entity.type/COMPANY");
    private static final Map<Object, Object> ENTITY_TYPE_FUND = Util.map(":db/ident", ":legal-entity.type/FUND");
    private static final Map<Object, Object> ENTITY_TYPE_SPV = Util.map(":db/ident", ":legal-entity.type/SPV");
    private static final Map<Object, Object> ENTITY_TYPE_GOVERNMENT = Util.map(":db/ident", ":legal-entity.type/GOVERNMENT");
    private static final Map<Object, Object> ENTITY_TYPE_SUPRANATIONAL = Util.map(":db/ident", ":legal-entity.type/SUPRANATIONAL");

    private static final Map<Object, Object> ENTITY_STATE_ACTIVE = Util.map(":db/ident", ":legal-entity.state/ACTIVE");
    private static final Map<Object, Object> ENTITY_STATE_MERGED = Util.map(":db/ident", ":legal-entity.state/MERGED");
    private static final Map<Object, Object> ENTITY_STATE_ACQUIRED = Util.map(":db/ident", ":legal-entity.state/ACQUIRED");

    private static final Map<Object, Object> SECURITY_TYPE_EQUITY = Util.map(":db/ident", ":security.type/EQUITY");
    private static final Map<Object, Object> SECURITY_TYPE_BOND = Util.map(":db/ident", ":security.type/BOND");

    private static final Map<Object, Object> SECURITY_STATE_ACTIVE = Util.map(":db/ident", ":security.state/ACTIVE");
    private static final Map<Object, Object> SECURITY_STATE_MERGED = Util.map(":db/ident", ":security.state/MERGED");
    private static final Map<Object, Object> SECURITY_STATE_SPLIT = Util.map(":db/ident", ":security.state/SPLIT");
    private static final Map<Object, Object> SECURITY_STATE_REDEEMED = Util.map(":db/ident", ":security.state/REDEEMED");

    private static final Map<Object, Object> ACTION_TYPE_MERGER = Util.map(":db/ident", ":corporate-action.type/MERGER");
    private static final Map<Object, Object> ACTION_TYPE_ACQUISITION = Util.map(":db/ident", ":corporate-action.type/ACQUISITION");
    private static final Map<Object, Object> ACTION_TYPE_SPIN_OFF = Util.map(":db/ident", ":corporate-action.type/SPIN_OFF");
    private static final Map<Object, Object> ACTION_TYPE_NAME_CHANGE = Util.map(":db/ident", ":corporate-action.type/NAME_CHANGE");
    private static final Map<Object, Object> ACTION_TYPE_STOCK_SPLIT = Util.map(":db/ident", ":corporate-action.type/STOCK_SPLIT");
    private static final Map<Object, Object> ACTION_TYPE_REDEMPTION = Util.map(":db/ident", ":corporate-action.type/REDEMPTION");

    @SuppressWarnings("unchecked")
    public static List<Object> allSchema() {
        return Util.list(
                LEGAL_ENTITY_ID, LEGAL_ENTITY_NAME, LEGAL_ENTITY_TYPE,
                LEGAL_ENTITY_STATE, LEGAL_ENTITY_ACTIVE, LEGAL_ENTITY_FOUNDED_DATE,
                SECURITY_ID, SECURITY_NAME, SECURITY_ISIN, SECURITY_TYPE,
                SECURITY_STATE, SECURITY_ISSUER, SECURITY_ISSUE_DATE,
                SECURITY_MATURITY_DATE, SECURITY_ACTIVE,
                CORPORATE_ACTION_ID, CORPORATE_ACTION_TYPE, CORPORATE_ACTION_VALID_DATE,
                CORPORATE_ACTION_DESCRIPTION, CORPORATE_ACTION_SPLIT_RATIO,
                SECURITY_LINEAGE_ID, SECURITY_LINEAGE_PARENT,
                SECURITY_LINEAGE_CHILD, SECURITY_LINEAGE_ACTION,
                LEGAL_ENTITY_LINEAGE_ID, LEGAL_ENTITY_LINEAGE_PARENT,
                LEGAL_ENTITY_LINEAGE_CHILD, LEGAL_ENTITY_LINEAGE_ACTION
        );
    }

    @SuppressWarnings("unchecked")
    public static List<Object> allEnums() {
        return Util.list(
                ENTITY_TYPE_COMPANY, ENTITY_TYPE_FUND, ENTITY_TYPE_SPV,
                ENTITY_TYPE_GOVERNMENT, ENTITY_TYPE_SUPRANATIONAL,
                ENTITY_STATE_ACTIVE, ENTITY_STATE_MERGED, ENTITY_STATE_ACQUIRED,
                SECURITY_TYPE_EQUITY, SECURITY_TYPE_BOND,
                SECURITY_STATE_ACTIVE, SECURITY_STATE_MERGED,
                SECURITY_STATE_SPLIT, SECURITY_STATE_REDEEMED,
                ACTION_TYPE_MERGER, ACTION_TYPE_ACQUISITION, ACTION_TYPE_SPIN_OFF,
                ACTION_TYPE_NAME_CHANGE, ACTION_TYPE_STOCK_SPLIT, ACTION_TYPE_REDEMPTION
        );
    }
}
