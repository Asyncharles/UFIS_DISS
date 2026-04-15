package com.ufis.repository;

import com.ufis.domain.enums.CorporateActionType;
import com.ufis.domain.enums.LegalEntityState;
import com.ufis.domain.enums.LegalEntityType;
import com.ufis.domain.enums.SecurityState;
import com.ufis.domain.enums.SecurityType;
import datomic.Util;

import java.util.Map;

public final class DatomicEnumMapper {
    private DatomicEnumMapper() {}

    private static final Map<LegalEntityType, Object> ENTITY_TYPE_TO_KEYWORD = Map.of(
            LegalEntityType.COMPANY, Util.read(":legal-entity.type/COMPANY"),
            LegalEntityType.FUND, Util.read(":legal-entity.type/FUND"),
            LegalEntityType.SPV, Util.read(":legal-entity.type/SPV"),
            LegalEntityType.GOVERNMENT, Util.read(":legal-entity.type/GOVERNMENT"),
            LegalEntityType.SUPRANATIONAL, Util.read(":legal-entity.type/SUPRANATIONAL")
    );

    private static final Map<LegalEntityState, Object> ENTITY_STATE_TO_KEYWORD = Map.of(
            LegalEntityState.ACTIVE, Util.read(":legal-entity.state/ACTIVE"),
            LegalEntityState.MERGED, Util.read(":legal-entity.state/MERGED"),
            LegalEntityState.ACQUIRED, Util.read(":legal-entity.state/ACQUIRED")
    );

    private static final Map<SecurityType, Object> SECURITY_TYPE_TO_KEYWORD = Map.of(
            SecurityType.EQUITY, Util.read(":security.type/EQUITY"),
            SecurityType.BOND, Util.read(":security.type/BOND")
    );

    private static final Map<SecurityState, Object> SECURITY_STATE_TO_KEYWORD = Map.of(
            SecurityState.ACTIVE, Util.read(":security.state/ACTIVE"),
            SecurityState.MERGED, Util.read(":security.state/MERGED"),
            SecurityState.SPLIT, Util.read(":security.state/SPLIT"),
            SecurityState.REDEEMED, Util.read(":security.state/REDEEMED")
    );

    private static final Map<CorporateActionType, Object> ACTION_TYPE_TO_KEYWORD = Map.of(
            CorporateActionType.MERGER, Util.read(":corporate-action.type/MERGER"),
            CorporateActionType.ACQUISITION, Util.read(":corporate-action.type/ACQUISITION"),
            CorporateActionType.SPIN_OFF, Util.read(":corporate-action.type/SPIN_OFF"),
            CorporateActionType.NAME_CHANGE, Util.read(":corporate-action.type/NAME_CHANGE"),
            CorporateActionType.STOCK_SPLIT, Util.read(":corporate-action.type/STOCK_SPLIT"),
            CorporateActionType.REDEMPTION, Util.read(":corporate-action.type/REDEMPTION")
    );

    public static Object toKeyword(LegalEntityType type) {
        return ENTITY_TYPE_TO_KEYWORD.get(type);
    }

    public static Object toKeyword(LegalEntityState state) {
        return ENTITY_STATE_TO_KEYWORD.get(state);
    }

    public static Object toKeyword(SecurityType type) {
        return SECURITY_TYPE_TO_KEYWORD.get(type);
    }

    public static Object toKeyword(SecurityState state) {
        return SECURITY_STATE_TO_KEYWORD.get(state);
    }

    public static Object toKeyword(CorporateActionType type) {
        return ACTION_TYPE_TO_KEYWORD.get(type);
    }

    public static Object resolveIdent(Object refValue) {
        if (refValue instanceof datomic.Entity e) {
            return e.get(":db/ident");
        }

        return refValue;
    }

    public static LegalEntityType toLegalEntityType(Object refValue) {
        String name = resolveIdent(refValue).toString();

        return LegalEntityType.valueOf(name.substring(name.lastIndexOf('/') + 1));
    }

    public static LegalEntityState toLegalEntityState(Object refValue) {
        String name = resolveIdent(refValue).toString();

        return LegalEntityState.valueOf(name.substring(name.lastIndexOf('/') + 1));
    }

    public static SecurityType toSecurityType(Object refValue) {
        String name = resolveIdent(refValue).toString();

        return SecurityType.valueOf(name.substring(name.lastIndexOf('/') + 1));
    }

    public static SecurityState toSecurityState(Object refValue) {
        String name = resolveIdent(refValue).toString();

        return SecurityState.valueOf(name.substring(name.lastIndexOf('/') + 1));
    }

    public static CorporateActionType toCorporateActionType(Object refValue) {
        String name = resolveIdent(refValue).toString();

        return CorporateActionType.valueOf(name.substring(name.lastIndexOf('/') + 1));
    }
}
