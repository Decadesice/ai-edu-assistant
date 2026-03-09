const url = new URL(window.location.href);
const hasQuery = url.searchParams.has("apiBase");
const fromQuery = hasQuery ? url.searchParams.get("apiBase") : null;
const fromStorage = localStorage.getItem("API_BASE");
const overrideFlag = localStorage.getItem("API_BASE_OVERRIDE");
function normalizeApiBase(base) {
  if (!base) return base;
  const trimmed = String(base).trim().replace(/\/$/, "");
  if (window.location.protocol === "https:" && trimmed.startsWith("http://")) {
    const httpsCandidate = "https://" + trimmed.slice("http://".length);
    return httpsCandidate;
  }
  return trimmed;
}

function isLocalHost(hostname) {
  return hostname === "localhost" || hostname === "127.0.0.1" || hostname === "::1";
}

function shouldUseStored(base) {
  try {
    const parsed = new URL(base);
    const currentHost = window.location.hostname;
    if (isLocalHost(currentHost)) {
      return isLocalHost(parsed.hostname) || overrideFlag === "1";
    }
    if (isLocalHost(parsed.hostname)) {
      return false;
    }
    return true;
  } catch {
    return false;
  }
}

// 内网穿透域名映射：前端域名 -> 后端地址
const TUNNEL_API_MAP = {
  "596b0986.r9.cpolar.cn": "https://62ac020b.r6.cpolar.top",
};

function deriveFromLocation() {
  if (window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1') {
    return "http://localhost:8081";
  }
  return window.location.origin;
}

const normalizedQuery = normalizeApiBase(fromQuery);
if (hasQuery) {
  if (normalizedQuery) {
    localStorage.setItem("API_BASE_OVERRIDE", "1");
  } else {
    localStorage.removeItem("API_BASE_OVERRIDE");
    localStorage.removeItem("API_BASE");
  }
}
const normalizedStorage = normalizeApiBase(fromStorage);
const resolvedBase =
  normalizedQuery ||
  (normalizedStorage && shouldUseStored(normalizedStorage) ? normalizedStorage : null) ||
  normalizeApiBase(deriveFromLocation());

window.API_BASE = resolvedBase || "";
