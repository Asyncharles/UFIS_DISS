import { createContext, useContext, useState, ReactNode, useCallback } from "react";

type BreadcrumbContextValue = {
  labels: Record<string, string>;
  setLabel: (key: string, label: string) => void;
};

const BreadcrumbContext = createContext<BreadcrumbContextValue>({
  labels: {},
  setLabel: () => {},
});

export function BreadcrumbProvider({ children }: { children: ReactNode }) {
  const [labels, setLabels] = useState<Record<string, string>>({});

  const setLabel = useCallback((key: string, label: string) => {
    setLabels((prev) => {
      if (prev[key] === label) return prev;
      return { ...prev, [key]: label };
    });
  }, []);

  return (
    <BreadcrumbContext.Provider value={{ labels, setLabel }}>
      {children}
    </BreadcrumbContext.Provider>
  );
}

export function useBreadcrumbLabel() {
  return useContext(BreadcrumbContext);
}
