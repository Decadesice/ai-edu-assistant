function isLocalHost(hostname) {
  return hostname === "localhost" || hostname === "127.0.0.1" || hostname === "::1";
}

function deriveDefaultApiBase() {
  if (typeof window === "undefined") {
    return "http://localhost:8081";
  }
  if (window.config && window.config.apiBaseUrl) {
    return window.config.apiBaseUrl;
  }
  const currentHost = window.location.hostname;
  if (currentHost === "localhost" || currentHost === "127.0.0.1") {
    return `${window.location.protocol}//${currentHost}:8081`;
  }
  return window.location.origin;
}

export function getApiBase() {
  const fromWindow = typeof window !== "undefined" ? window.API_BASE : null;
  const fromStorage = typeof localStorage !== "undefined" ? localStorage.getItem("API_BASE") : null;
  return String(fromWindow || fromStorage || deriveDefaultApiBase()).replace(/\/$/, "");
}

export async function apiFetch(path, options = {}) {
  const url = path.startsWith("http") ? path : `${getApiBase()}${path}`;
  const token = localStorage.getItem("token");
  const headers = {
    ...(options.headers || {}),
    "Content-Type": "application/json"
  };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return fetch(url, { ...options, headers });
}
