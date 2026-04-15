import { ReactNode } from "react";
import clsx from "clsx";

type SummaryRow = {
  label: string;
  value: ReactNode;
};

type SummaryCardProps = {
  eyebrow?: string;
  title: string;
  highlight?: ReactNode;
  rows: SummaryRow[];
  accent?: "active" | "historical" | "terminal";
};

export function SummaryCard({ eyebrow, title, highlight, rows, accent }: SummaryCardProps) {
  return (
    <section
      className={clsx("panel summary-card", accent && `accent-${accent}`)}
    >
      {eyebrow ? <div className="panel-kicker">{eyebrow}</div> : null}
      <div className="summary-value">{title}</div>
      {highlight ? <div className="summary-highlight">{highlight}</div> : null}
      <table className="summary-table">
        <tbody>
          {rows.map((row) => (
            <tr key={row.label}>
              <td>{row.label}</td>
              <td>{row.value}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
