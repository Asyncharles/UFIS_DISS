import { ZodType } from "zod";

import {
  corporateActionDetailResponseSchema,
  corporateActionRecordSchema,
  legalEntityActionListEntrySchema,
  legalEntityLineageResponseSchema,
  searchLegalEntityResultSchema,
  searchResponseSchema,
  searchSecurityResultSchema,
  dummyDataSeedResponseSchema,
  securityActionListEntrySchema,
  securityLineageResponseSchema,
} from "./schemas";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "/api";

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

function buildQuery(params: Record<string, string | undefined>) {
  const searchParams = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value) {
      searchParams.set(key, value);
    }
  });
  const queryString = searchParams.toString();
  return queryString ? `?${queryString}` : "";
}

async function request<T>(path: string, schema: ZodType<T>, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      Accept: "application/json",
      ...init?.headers,
    },
    ...init,
  });

  if (!response.ok) {
    const fallbackMessage = `${response.status} ${response.statusText}`;
    const rawBody = await response.text();
    let message = fallbackMessage;

    if (rawBody) {
      try {
        const payload = JSON.parse(rawBody) as { detail?: string; message?: string; error?: string };
        message = payload.detail ?? payload.message ?? payload.error ?? rawBody;
      } catch {
        message = rawBody;
      }
    }

    throw new ApiError(response.status, message);
  }

  const payload = await response.json();
  return schema.parse(payload);
}

async function requestWithoutBody<T>(path: string, schema: ZodType<T>, method: string): Promise<T> {
  return request(path, schema, { method });
}

export const api = {
  search(query: string) {
    return request(`/search${buildQuery({ q: query })}`, searchResponseSchema);
  },
  getSecurityLineage(id: string, validAt?: string) {
    return request(
      `/security/${id}/lineage${buildQuery({ validAt })}`,
      securityLineageResponseSchema,
    );
  },
  getSecurityAuditLineage(id: string, validAt: string, knownAt: string) {
    return request(
      `/security/${id}/lineage/audit${buildQuery({ validAt, knownAt })}`,
      securityLineageResponseSchema,
    );
  },
  getSecurityActions(id: string) {
    return request(`/security/${id}/actions`, securityActionListEntrySchema.array());
  },
  getLegalEntityLineage(id: string, validAt?: string) {
    return request(
      `/legal-entity/${id}/lineage${buildQuery({ validAt })}`,
      legalEntityLineageResponseSchema,
    );
  },
  getLegalEntityActions(id: string) {
    return request(`/legal-entity/${id}/actions`, legalEntityActionListEntrySchema.array());
  },
  getRecentActions(limit = 20) {
    return request(
      `/corporate-action/recent${buildQuery({ limit: String(limit) })}`,
      corporateActionRecordSchema.array(),
    );
  },
  getCorporateAction(id: string) {
    return request(`/corporate-action/${id}`, corporateActionRecordSchema);
  },
  getCorporateActionDetail(id: string) {
    return request(`/corporate-action/${id}/detail`, corporateActionDetailResponseSchema);
  },
  browseSecurities(type?: string, state?: string) {
    return request(`/security${buildQuery({ type, state })}`, searchSecurityResultSchema.array());
  },
  browseLegalEntities(type?: string, state?: string) {
    return request(`/legal-entity${buildQuery({ type, state })}`, searchLegalEntityResultSchema.array());
  },
  seedDummyData(tier: "SMALL" | "MEDIUM" | "LARGE" = "SMALL") {
    return requestWithoutBody(
      `/admin/seed-dummy-data${buildQuery({ tier })}`,
      dummyDataSeedResponseSchema,
      "POST",
    );
  },
};
