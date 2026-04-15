import { NameHistoryResponse } from "../api/schemas";
import { formatDateOnly, formatIdentifier } from "../lib/format";

type NameHistoryTableProps = {
  nameHistory: NameHistoryResponse;
};

export function NameHistoryTable({ nameHistory }: NameHistoryTableProps) {
  const hasSecurityHistory = nameHistory.security.length > 0;
  const hasIssuerHistory = nameHistory.issuer.length > 0;

  if (!hasSecurityHistory && !hasIssuerHistory) {
    return <div className="empty-state">No name changes recorded.</div>;
  }

  return (
    <div className="stack">
      {hasSecurityHistory ? (
        <section>
          <div className="panel-kicker">Security Name History</div>
          <table className="data-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Previous</th>
                <th>New</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {nameHistory.security.map((entry) => (
                <tr key={entry.actionId}>
                  <td className="mono">{formatDateOnly(entry.validDate)}</td>
                  <td className="cell-previous">{entry.previousName}</td>
                  <td className="cell-new">{entry.newName}</td>
                  <td className="mono text-muted">{formatIdentifier(entry.actionId)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      ) : null}

      {hasIssuerHistory ? (
        <section>
          <div className="panel-kicker">Issuer Name History</div>
          <table className="data-table">
            <thead>
              <tr>
                <th>Date</th>
                <th>Previous</th>
                <th>New</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {nameHistory.issuer.map((entry) => (
                <tr key={entry.actionId}>
                  <td className="mono">{formatDateOnly(entry.validDate)}</td>
                  <td className="cell-previous">{entry.previousName}</td>
                  <td className="cell-new">{entry.newName}</td>
                  <td className="mono text-muted">{formatIdentifier(entry.actionId)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      ) : null}
    </div>
  );
}
