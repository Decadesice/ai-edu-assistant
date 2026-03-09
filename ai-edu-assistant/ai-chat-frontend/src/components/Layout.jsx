import React, { useEffect, useState } from "react";
import { NavLink, Outlet, useLocation, useNavigate } from "react-router-dom";
import { apiFetch } from "../services/api.js";

export default function Layout() {
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState(localStorage.getItem("username") || "");

  function clearAuth() {
    localStorage.removeItem("token");
    localStorage.removeItem("userId");
    localStorage.removeItem("username");
  }

  function requireRelogin(text) {
    sessionStorage.setItem("LOGIN_MESSAGE", text);
    clearAuth();
    navigate("/login", { replace: true, state: { message: text } });
  }

  async function refreshStatus() {
    try {
      const resp = await apiFetch("/api/unified/conversation/list/detail", { method: "GET" });
      if (!resp.ok) {
        requireRelogin("登录已失效，请重新登录");
      }
    } catch {
      requireRelogin("连接异常，请重新登录");
    }
  }

  useEffect(() => {
    refreshStatus();
  }, []);

  useEffect(() => {
    function sync() {
      setUsername(localStorage.getItem("username") || "");
    }
    window.addEventListener("storage", sync);
    return () => window.removeEventListener("storage", sync);
  }, []);

  function logout() {
    clearAuth();
    navigate("/login", { replace: true });
  }

  const isQaPage = location.pathname === "/qa";
  if (isQaPage) {
    return <Outlet />;
  }

  return (
    <div className="app">
      <header className="topbar">
        <div className="topbar-left">
          <div className="brand">
            <img className="brand-logo-img" src="/favicon1.png" alt="题舟" />
            <div className="brand-text">
              <div className="brand-title">题舟</div>
              <div className="brand-slogan">题海如川，智舟为渡</div>
            </div>
          </div>
        </div>
        <div className="topbar-right">
          <button className="ghost-btn" type="button" onClick={refreshStatus}>
            刷新
          </button>
          <div className="user-chip">{username ? `欢迎，${username}` : "已登录"}</div>
          <button className="ghost-btn" type="button" onClick={logout}>
            退出登录
          </button>
        </div>
      </header>

      <nav className="nav">
        <div className="nav-inner">
          <NavLink to="/" end className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}>
            首页
          </NavLink>
          <NavLink to="/knowledge" className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}>
            知识管理
          </NavLink>
          <NavLink to="/qa" className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}>
            智能问答
          </NavLink>
          <NavLink to="/generate" className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}>
            题目生成
          </NavLink>
          <NavLink to="/stats" className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}>
            学习统计
          </NavLink>
        </div>
      </nav>

      <main className="main">
        <Outlet />
      </main>

      <footer className="footer">题舟 · 题海如川，智舟为渡 © 2026</footer>
    </div>
  );
}
