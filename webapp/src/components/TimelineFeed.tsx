import { useNavigate } from "@tanstack/react-router";

import { CorporateActionRecord } from "../api/schemas";
import { formatDateOnly, formatIdentifier } from "../lib/format";

type TimelineFeedProps = {
  entries: Array<{
    action: CorporateActionRecord;
    role?: string;
  }>;
};

function actionTypePillClass(type: string): string {
  switch (type) {
    case "MERGER":
    case "ACQUISITION":
      return "pill-terminal";
    case "STOCK_SPLIT":
    case "SPIN_OFF":
      return "pill-historical";
    case "NAME_CHANGE":
      return "pill-active";
    case "REDEMPTION":
      return "pill-terminal";
    default:
      return "";
  }
}

export function TimelineFeed({ entries }: TimelineFeedProps) {
  const navigate = useNavigate();

  if (entries.length === 0) {
    return <div className="empty-state">No actions recorded for the current selection.</div>;
  }

  return (
    <div className="timeline-list">
      {entries.map((entry) => (
        <button
          className="timeline-item"
          key={entry.action.id}
          onClick={() =>
            navigate({
              to: "/corporate-action/$id",
              params: { id: entry.action.id },
            })
          }
          type="button"
        >
          <div className="timeline-date">{formatDateOnly(entry.action.validDate)}</div>
          <div className="timeline-body">
            <div className="timeline-title">
              <span className={`pill ${actionTypePillClass(entry.action.type)}`} style={{ marginRight: 8 }}>
                {entry.action.type.replace("_", " ")}
              </span>
              {entry.role ? <span className="pill">{entry.role}</span> : null}
            </div>
            <div className="timeline-desc">{entry.action.description}</div>
            <div className="mono text-muted" style={{ fontSize: "0.7rem" }}>
              {formatIdentifier(entry.action.id)}
            </div>
          </div>
        </button>
      ))}
    </div>
  );
}
