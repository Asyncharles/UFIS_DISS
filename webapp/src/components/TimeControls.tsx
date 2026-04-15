import { useEffect, useState } from "react";

import { fromDateTimeLocalValue, toDateTimeLocalValue } from "../lib/format";

type TimeControlsProps = {
  validAt?: string;
  knownAt?: string;
  onApply: (next: { validAt?: string; knownAt?: string }) => void;
};

export function TimeControls({ validAt, knownAt, onApply }: TimeControlsProps) {
  const [validAtDraft, setValidAtDraft] = useState(toDateTimeLocalValue(validAt));
  const [knownAtDraft, setKnownAtDraft] = useState(toDateTimeLocalValue(knownAt));

  useEffect(() => {
    setValidAtDraft(toDateTimeLocalValue(validAt));
  }, [validAt]);

  useEffect(() => {
    setKnownAtDraft(toDateTimeLocalValue(knownAt));
  }, [knownAt]);

  return (
    <section className="panel panel-padding stack">
      <div>
        <div className="panel-kicker">Temporal Context</div>
      </div>
      <label className="stack">
        <span className="text-muted mono" style={{ fontSize: "0.7rem", textTransform: "uppercase", letterSpacing: "0.06em" }}>
          Valid at
        </span>
        <input
          className="time-input"
          onChange={(event) => setValidAtDraft(event.target.value)}
          type="datetime-local"
          value={validAtDraft}
        />
      </label>
      {knownAt !== undefined ? (
        <label className="stack">
          <span className="text-muted mono" style={{ fontSize: "0.7rem", textTransform: "uppercase", letterSpacing: "0.06em" }}>
            Known at
          </span>
          <input
            className="time-input"
            onChange={(event) => setKnownAtDraft(event.target.value)}
            type="datetime-local"
            value={knownAtDraft}
          />
        </label>
      ) : null}
      <div className="search-form">
        <button
          className="btn-primary"
          onClick={() =>
            onApply({
              validAt: fromDateTimeLocalValue(validAtDraft),
              knownAt: knownAt === undefined ? undefined : fromDateTimeLocalValue(knownAtDraft),
            })
          }
          type="button"
        >
          Apply
        </button>
        <button
          className="btn-secondary"
          onClick={() => {
            setValidAtDraft("");
            setKnownAtDraft("");
            onApply({ validAt: undefined, knownAt: undefined });
          }}
          type="button"
        >
          Clear
        </button>
      </div>
    </section>
  );
}
