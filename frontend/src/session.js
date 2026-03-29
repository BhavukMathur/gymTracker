const TOKEN_KEY = "gymtracker.session.token";

export function getStoredToken() {
  return localStorage.getItem(TOKEN_KEY) || "";
}

export function setStoredToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token);
  else localStorage.removeItem(TOKEN_KEY);
}

export function clearStoredToken() {
  localStorage.removeItem(TOKEN_KEY);
}

/** Read `sub` from JWT payload for display (no signature verification). */
export function getUsernameFromToken(token) {
  if (!token) return "";
  try {
    const segment = token.split(".")[1];
    if (!segment) return "";
    const base64 = segment.replace(/-/g, "+").replace(/_/g, "/");
    const pad = (4 - (base64.length % 4)) % 4;
    const padded = base64 + "=".repeat(pad);
    const binary = atob(padded);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    const payload = JSON.parse(new TextDecoder().decode(bytes));
    return typeof payload.sub === "string" ? payload.sub : "";
  } catch {
    return "";
  }
}
