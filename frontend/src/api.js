/** In dev, Vite proxies /api to the Spring backend. In production, set VITE_API_BASE if needed. */
export function apiBase() {
  if (import.meta.env.DEV) return "";
  return import.meta.env.VITE_API_BASE || "http://localhost:8080";
}

export function authHeader(token) {
  return { Authorization: `Bearer ${token}` };
}
