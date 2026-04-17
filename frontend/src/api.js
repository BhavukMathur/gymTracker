/** In dev, Vite proxies /api to the Spring backend. In production, set VITE_API_BASE if needed. */
export function apiBase() {
  if (import.meta.env.DEV) return "";
  return import.meta.env.VITE_API_BASE || "http://localhost:8080";
}

/** LangChain coach service. In dev, Vite proxies /coach to the coach process. Set VITE_COACH_BASE when the UI is hosted without that proxy. */
export function coachBase() {
  const raw = import.meta.env.VITE_COACH_BASE;
  if (raw !== undefined && raw !== null && String(raw).trim() !== "") {
    return String(raw).replace(/\/$/, "");
  }
  return "";
}

export function authHeader(token) {
  return { Authorization: `Bearer ${token}` };
}
