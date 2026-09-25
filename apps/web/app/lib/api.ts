export class ApiError extends Error {
  constructor(message: string, public status: number, public retryAfter = 0, public correlationId?: string) {
    super(message);
    this.name = "ApiError";
  }
}

async function requireSuccess(response: Response) {
  if (response.ok) return;
  const error = await response.json().catch(() => ({}));
  const seconds = Number(response.headers.get("Retry-After"));
  throw new ApiError(
    error.message || "Não foi possível concluir. Tente novamente.",
    response.status,
    Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds) : 0,
    error.correlation_id || response.headers.get("X-Correlation-ID") || undefined,
  );
}

export async function api(path: string, method = "GET", body?: unknown) {
  const headers: Record<string, string> = {};
  if (method !== "GET") {
    const response = await fetch("/api/v1/auth/csrf", { cache: "no-store" });
    await requireSuccess(response);
    const csrf = await response.json();
    headers[csrf.headerName] = csrf.token;
    headers["Content-Type"] = body instanceof Blob ? "application/octet-stream" : "application/json";
  }
  const response = await fetch(`/api/v1${path}`, {
    method, headers, cache: "no-store", body: body === undefined ? undefined : body instanceof Blob ? body : JSON.stringify(body),
  });
  if (response.status === 401 && !path.startsWith("/auth/")) window.location.assign(path.startsWith("/platform/") ? "/entrar?destino=plataforma" : "/entrar");
  await requireSuccess(response);
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
