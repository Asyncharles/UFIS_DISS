import { useNavigate } from "@tanstack/react-router";

import { CorporateActionRecord } from "../api/schemas";
import { formatDateOnly, formatIdentifier } from "../lib/format";

type ActionFeedProps = {
  actions: CorporateActionRecord[];
};

export function ActionFeed({ actions }: ActionFeedProps) {
  const navigate = useNavigate();

  if (actions.length === 0) {
    return <div className="empty-state">No corporate actions recorded yet.</div>;
  }

  return (
    <div className="timeline-list">
      {actions.map((action) => (
        <button
          className="timeline-item"
          key={action.id}
          onClick={() =>
            navigate({
              to: "/corporate-action/$id",
              params: { id: action.id },
            })
          }
          type="button"
        >
          <div className="timeline-date">{formatDateOnly(action.validDate)}</div>
          <div className="timeline-body">
            <div className="timeline-title">
              <span className="pill" style={{ marginRight: 8 }}>
                {action.type.replace("_", " ")}
              </span>
            </div>
            <div className="timeline-desc">{action.description}</div>
            <div className="mono text-muted" style={{ fontSize: "0.7rem" }}>
              {formatIdentifier(action.id)}
            </div>
          </div>
        </button>
      ))}
    </div>
  );
}
