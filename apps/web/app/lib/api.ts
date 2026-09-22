export async function api(path: string, method = "GET", body?: unknown) {
  const headers: Record<string, string> = {};
  if (method !== "GET") {
    const response = await fetch("/api/v1/auth/csrf", { cache: "no-store" });
    if (!response.ok) throw new Error("Não foi possível iniciar uma sessão segura.");
    const csrf = await response.json();
    headers[csrf.headerName] = csrf.token;
    headers["Content-Type"] = "application/json";
  }
  const response = await fetch(`/api/v1${path}`, { method, headers, cache: "no-store", body: body === undefined ? undefined : JSON.stringify(body) });
  if (!response.ok) {
    if (response.status === 401 && !path.startsWith("/auth/")) window.location.assign("/entrar");
    const error = await response.json().catch(() => ({}));
    throw new Error(error.message || "Não foi possível concluir. Tente novamente.");
  }
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
