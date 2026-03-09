const url = new URL(window.location.href);
const fromQuery = url.searchParams.get("apiBase");
const fromStorage = localStorage.getItem("API_BASE");
const currentHost = window.location.hostname;
const isLocalHost = currentHost === "localhost" || currentHost === "127.0.0.1" || currentHost === "::1";
const currentPort = window.location.port;
const derived =
  isLocalHost
    ? "http://localhost:8081"
    : window.location.origin;
window.API_BASE = (fromQuery || fromStorage || derived).replace(/\/$/, "");
