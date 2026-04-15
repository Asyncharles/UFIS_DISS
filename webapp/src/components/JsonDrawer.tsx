import { useState } from "react";

type JsonDrawerProps = {
  label?: string;
  value: unknown;
};

export function JsonDrawer({ label = "Raw JSON", value }: JsonDrawerProps) {
  const [copied, setCopied] = useState(false);
  const json = JSON.stringify(value, null, 2);

  function handleCopy() {
    navigator.clipboard.writeText(json).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  }

  return (
    <details className="json-drawer panel">
      <summary>{label}</summary>
      <div className="json-drawer-content">
        <button className="json-copy-btn" onClick={handleCopy} type="button">
          {copied ? "Copied" : "Copy"}
        </button>
        <pre>{json}</pre>
      </div>
    </details>
  );
}
