import React, { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { apiFetch } from "../services/api.js";

export default function Login() {
  const navigate = useNavigate();
  const location = useLocation();
  const [activeTab, setActiveTab] = useState("login");
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [email, setEmail] = useState("");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState({ type: "", text: "" });

  function showError(text) {
    setMessage({ type: "error", text });
  }
  function showSuccess(text) {
    setMessage({ type: "success", text });
  }

  useEffect(() => {
    if (window.top !== window.self) {
      const token = localStorage.getItem("token");
      if (token) {
        window.top.location.href = "/#/";
      }
    }
  }, []);

  useEffect(() => {
    const fromState = location.state && typeof location.state === "object" ? location.state.message : "";
    const fromSession = sessionStorage.getItem("LOGIN_MESSAGE") || "";
    const text = (fromState || fromSession || "").trim();
    if (text) {
      showError(text);
      sessionStorage.removeItem("LOGIN_MESSAGE");
    }
  }, [location.state]);

  async function doLogin() {
    if (!username.trim() || !password) {
      showError("请填写所有字段");
      return;
    }
    setLoading(true);
    try {
      const resp = await apiFetch("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ username: username.trim(), password })
      });
      const data = await resp.json().catch(() => ({}));
      if (resp.ok && data.token) {
        localStorage.setItem("token", data.token);
        localStorage.setItem("userId", data.userId);
        localStorage.setItem("username", data.username || username.trim());
        showSuccess("登录成功！正在跳转...");
        if (window.top !== window.self) {
          window.top.location.href = "/#/";
        } else {
          navigate("/", { replace: true });
        }
      } else {
        showError(data.message || "登录失败");
      }
    } catch {
      showError("网络错误，请稍后重试");
    } finally {
      setLoading(false);
    }
  }

  async function doRegister() {
    if (!username.trim() || !email.trim() || !password) {
      showError("请填写所有字段");
      return;
    }
    setLoading(true);
    try {
      const resp = await apiFetch("/api/auth/register", {
        method: "POST",
        body: JSON.stringify({
          username: username.trim(),
          email: email.trim(),
          password
        })
      });
      const data = await resp.json().catch(() => ({}));
      if (resp.ok) {
        showSuccess("注册成功，请登录");
        setActiveTab("login");
      } else {
        showError(data.message || "注册失败");
      }
    } catch {
      showError("网络错误，请稍后重试");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-page">
      <div className="login-card">
        <div className="login-header">
          <img className="login-logo" src="/favicon1.png" alt="题舟" />
          <div className="login-slogan">题海如川，智舟为渡</div>
          <div className="login-subtitle">登录后进入主界面</div>
        </div>

        <div className="tabs">
          <button
            type="button"
            className={`tab ${activeTab === "login" ? "active" : ""}`}
            onClick={() => setActiveTab("login")}
          >
            登录
          </button>
          <button
            type="button"
            className={`tab ${activeTab === "register" ? "active" : ""}`}
            onClick={() => setActiveTab("register")}
          >
            注册
          </button>
        </div>

        {message.text && (
          <div className={message.type === "success" ? "msg success" : "msg error"}>
            {message.text}
          </div>
        )}

        {activeTab === "login" ? (
          <div className="form">
            <label className="field">
              <div className="label">用户名</div>
              <input value={username} onChange={(e) => setUsername(e.target.value)} />
            </label>
            <label className="field">
              <div className="label">密码</div>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
            </label>
            <button className="primary-btn full" disabled={loading} type="button" onClick={doLogin}>
              {loading ? "处理中..." : "登录"}
            </button>
          </div>
        ) : (
          <div className="form">
            <label className="field">
              <div className="label">用户名</div>
              <input value={username} onChange={(e) => setUsername(e.target.value)} />
            </label>
            <label className="field">
              <div className="label">邮箱</div>
              <input value={email} onChange={(e) => setEmail(e.target.value)} />
            </label>
            <label className="field">
              <div className="label">密码</div>
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
            </label>
            <button className="primary-btn full" disabled={loading} type="button" onClick={doRegister}>
              {loading ? "处理中..." : "注册"}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
