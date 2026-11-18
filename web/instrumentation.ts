export async function register() {
  const dsn = process.env.SENTRY_DSN;
  if (dsn && process.env.NEXT_RUNTIME === "nodejs") {
    const Sentry = await import("@sentry/node");
    Sentry.init({
      dsn,
      dataCollection: {
        userInfo: false,
        cookies: false,
        httpHeaders: false,
        httpBodies: [],
        urlQueryParams: false,
        stackFrameVariables: false,
      },
      beforeSend(event) {
        delete event.request;
        delete event.breadcrumbs;
        return event;
      },
    });
  }
}

export async function onRequestError(error: unknown) {
  if (process.env.SENTRY_DSN && process.env.NEXT_RUNTIME === "nodejs") {
    const Sentry = await import("@sentry/node");
    Sentry.captureException(error);
  }
}
