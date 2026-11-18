"use client";

import * as Sentry from "@sentry/browser";
import { useEffect } from "react";

export default function ErrorPage({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => { Sentry.captureException(error); }, [error]);

  return (
    <div className="panel">
      <h1>Algo deu errado</h1>
      <button className="mt-4" onClick={reset}>Tentar novamente</button>
    </div>
  );
}
