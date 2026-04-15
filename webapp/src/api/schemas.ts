import { z } from "zod";

const uuidSchema = z.string().uuid();
const instantSchema = z.string().datetime({ offset: true });

export const securityTypeSchema = z.enum(["EQUITY", "BOND"]);
export const securityStateSchema = z.enum(["ACTIVE", "MERGED", "SPLIT", "REDEEMED"]);
export const legalEntityTypeSchema = z.enum(["COMPANY", "FUND", "SPV", "GOVERNMENT", "SUPRANATIONAL"]);
export const legalEntityStateSchema = z.enum(["ACTIVE", "MERGED", "ACQUIRED"]);
export const corporateActionTypeSchema = z.enum([
  "MERGER",
  "ACQUISITION",
  "SPIN_OFF",
  "NAME_CHANGE",
  "STOCK_SPLIT",
  "REDEMPTION",
]);
export const securityRoleSchema = z.enum(["SOURCE", "RESULT", "SUBJECT"]);
export const legalEntityRoleSchema = z.enum(["SOURCE", "RESULT", "SUBJECT", "ACQUIRER"]);

export const issuerRefSchema = z.object({
  id: uuidSchema,
  name: z.string(),
});

export const securityResponseSchema = z.object({
  id: uuidSchema,
  name: z.string(),
  isin: z.string().nullable().optional(),
  type: securityTypeSchema,
  state: securityStateSchema,
  active: z.boolean(),
  issueDate: instantSchema,
  maturityDate: instantSchema.nullable().optional(),
  issuer: issuerRefSchema.nullable().optional(),
});

export const legalEntityResponseSchema = z.object({
  id: uuidSchema,
  name: z.string(),
  type: legalEntityTypeSchema,
  state: legalEntityStateSchema,
  active: z.boolean(),
  foundedDate: instantSchema,
});

export const corporateActionRecordSchema = z.object({
  id: uuidSchema,
  type: corporateActionTypeSchema,
  validDate: instantSchema,
  description: z.string(),
  splitRatio: z.string().nullable().optional(),
});

export const securityLineageEntrySchema = z.object({
  parentId: uuidSchema,
  parentName: z.string(),
  parentIsin: z.string().nullable().optional(),
  parentType: securityTypeSchema,
  parentState: securityStateSchema,
  parentActive: z.boolean(),
  parentIssueDate: instantSchema,
  parentMaturityDate: instantSchema.nullable().optional(),
  issuer: issuerRefSchema.nullable().optional(),
  actionId: uuidSchema,
  actionType: corporateActionTypeSchema,
  actionDate: instantSchema,
});

export const legalEntityLineageEntrySchema = z.object({
  parentId: uuidSchema,
  parentName: z.string(),
  parentType: legalEntityTypeSchema,
  parentState: legalEntityStateSchema,
  parentActive: z.boolean(),
  parentFoundedDate: instantSchema,
  actionId: uuidSchema,
  actionType: corporateActionTypeSchema,
  actionDate: instantSchema,
});

export const securityNameHistoryEntrySchema = z.object({
  validDate: instantSchema,
  previousName: z.string(),
  newName: z.string(),
  previousIsin: z.string().nullable().optional(),
  newIsin: z.string().nullable().optional(),
  actionId: uuidSchema,
});

export const issuerNameHistoryEntrySchema = z.object({
  validDate: instantSchema,
  previousName: z.string(),
  newName: z.string(),
  actionId: uuidSchema,
});

export const nameHistorySchema = z.object({
  security: z.array(securityNameHistoryEntrySchema),
  issuer: z.array(issuerNameHistoryEntrySchema),
});

export const securityLineageResponseSchema = z.object({
  security: securityResponseSchema,
  securityLineage: z.array(securityLineageEntrySchema),
  currentIssuer: legalEntityResponseSchema.nullable().optional(),
  issuerLineage: z.array(legalEntityLineageEntrySchema),
  nameHistory: nameHistorySchema,
  resolvedAt: instantSchema.nullable().optional(),
});

export const legalEntityLineageResponseSchema = z.object({
  legalEntity: legalEntityResponseSchema,
  issuerLineage: z.array(legalEntityLineageEntrySchema),
  nameHistory: nameHistorySchema,
  resolvedAt: instantSchema.nullable().optional(),
});

export const securityActionListEntrySchema = z.object({
  action: corporateActionRecordSchema,
  role: securityRoleSchema,
});

export const legalEntityActionListEntrySchema = z.object({
  action: corporateActionRecordSchema,
  role: legalEntityRoleSchema,
});

export const searchSecurityResultSchema = z.object({
  id: uuidSchema,
  name: z.string(),
  isin: z.string().nullable().optional(),
  type: securityTypeSchema,
  state: securityStateSchema,
  active: z.boolean(),
  issuerId: uuidSchema.nullable().optional(),
  issuerName: z.string().nullable().optional(),
});

export const searchLegalEntityResultSchema = z.object({
  id: uuidSchema,
  name: z.string(),
  type: legalEntityTypeSchema,
  state: legalEntityStateSchema,
  active: z.boolean(),
});

export const searchCorporateActionResultSchema = z.object({
  id: uuidSchema,
  type: corporateActionTypeSchema,
  validDate: instantSchema,
  description: z.string(),
});

export const searchResponseSchema = z.object({
  securities: z.array(searchSecurityResultSchema),
  legalEntities: z.array(searchLegalEntityResultSchema),
  corporateActions: z.array(searchCorporateActionResultSchema),
});

export const lineageRecordResponseSchema = z.object({
  id: uuidSchema,
  actionId: uuidSchema,
  parentSecurityId: uuidSchema.nullable(),
  childSecurityId: uuidSchema.nullable(),
  parentEntityId: uuidSchema.nullable(),
  childEntityId: uuidSchema.nullable(),
});

export const corporateActionDetailResponseSchema = z.object({
  action: corporateActionRecordSchema,
  sourceSecurities: z.array(securityResponseSchema),
  resultSecurities: z.array(securityResponseSchema),
  terminatedSecurities: z.array(securityResponseSchema),
  sourceEntities: z.array(legalEntityResponseSchema),
  resultEntities: z.array(legalEntityResponseSchema),
  lineageRecords: z.array(lineageRecordResponseSchema),
});

export const dummyDataSeedResponseSchema = z.object({
  tier: z.enum(["SMALL", "MEDIUM", "LARGE"]),
  initialEntities: z.number().int(),
  initialSecurities: z.number().int(),
  corporateActions: z.number().int(),
  sampleEntityId: uuidSchema.nullable(),
  sampleSecurityId: uuidSchema.nullable(),
  sampleActionId: uuidSchema.nullable(),
  searchHint: z.string(),
  auditHintTimestamps: z.array(instantSchema),
});

export type SecurityResponse = z.infer<typeof securityResponseSchema>;
export type LegalEntityResponse = z.infer<typeof legalEntityResponseSchema>;
export type CorporateActionRecord = z.infer<typeof corporateActionRecordSchema>;
export type SecurityLineageResponse = z.infer<typeof securityLineageResponseSchema>;
export type LegalEntityLineageResponse = z.infer<typeof legalEntityLineageResponseSchema>;
export type SecurityActionListEntry = z.infer<typeof securityActionListEntrySchema>;
export type LegalEntityActionListEntry = z.infer<typeof legalEntityActionListEntrySchema>;
export type NameHistoryResponse = z.infer<typeof nameHistorySchema>;
export type SearchResponse = z.infer<typeof searchResponseSchema>;
export type SearchSecurityResult = z.infer<typeof searchSecurityResultSchema>;
export type SearchLegalEntityResult = z.infer<typeof searchLegalEntityResultSchema>;
export type SearchCorporateActionResult = z.infer<typeof searchCorporateActionResultSchema>;
export type LineageRecordResponse = z.infer<typeof lineageRecordResponseSchema>;
export type CorporateActionDetailResponse = z.infer<typeof corporateActionDetailResponseSchema>;
export type DummyDataSeedResponse = z.infer<typeof dummyDataSeedResponseSchema>;
