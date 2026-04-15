import React from "react";
import ReactDOM from "react-dom/client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { RouterProvider } from "@tanstack/react-router";
import "reactflow/dist/style.css";

import { BreadcrumbProvider } from "./app/BreadcrumbContext";
import { router } from "./app/router";
import "./styles/base.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
      refetchOnWindowFocus: false,
    },
  },
});

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <QueryClientProvider client={queryClient}>
      <BreadcrumbProvider>
        <RouterProvider router={router} />
      </BreadcrumbProvider>
    </QueryClientProvider>
  </React.StrictMode>,
);
