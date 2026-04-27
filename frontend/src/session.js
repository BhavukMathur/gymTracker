const TOKEN_KEY = "gymtracker.session.token";
const DISMISSED_ANNOUNCEMENTS_PREFIX = "gymtracker.dismissedAnnouncements.";

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

/**
 * Read `roles` from JWT (no verification). New logins include roles; older sessions may be empty.
 * @returns {string[]}
 */
export function getRolesFromToken(token) {
  if (!token) return [];
  try {
    const segment = token.split(".")[1];
    if (!segment) return [];
    const base64 = segment.replace(/-/g, "+").replace(/_/g, "/");
    const pad = (4 - (base64.length % 4)) % 4;
    const padded = base64 + "=".repeat(pad);
    const binary = atob(padded);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
    const payload = JSON.parse(new TextDecoder().decode(bytes));
    const roles = payload.roles;
    if (Array.isArray(roles)) {
      return roles.filter((r) => typeof r === "string");
    }
    return [];
  } catch {
    return [];
  }
}

/** @returns {Set<string>} */
export function getDismissedAnnouncementIds(username) {
  if (!username) return new Set();
  try {
    const raw = localStorage.getItem(`${DISMISSED_ANNOUNCEMENTS_PREFIX}${username}`);
    const parsed = raw ? JSON.parse(raw) : [];
    if (!Array.isArray(parsed)) return new Set();
    return new Set(parsed.map((id) => String(id)));
  } catch {
    return new Set();
  }
}

/** @param {string} username */
export function persistDismissedAnnouncementIds(username, ids) {
  if (!username) return;
  localStorage.setItem(
    `${DISMISSED_ANNOUNCEMENTS_PREFIX}${username}`,
    JSON.stringify([...ids])
  );
}
