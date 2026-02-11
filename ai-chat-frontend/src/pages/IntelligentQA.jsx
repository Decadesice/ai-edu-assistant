import React, { useEffect, useMemo } from "react";
import { useNavigate } from "react-router-dom";
import { getApiBase } from "../services/api.js";

export default function IntelligentQA() {
  const src = useMemo(() => "/qa/chat.html", []);
  const navigate = useNavigate();

  useEffect(() => {
    const base = getApiBase();
    if (base) {
      localStorage.setItem("API_BASE", base);
    }
  }, []);

  return (
    <div className="page qa-page">
      <button className="qa-back-btn" type="button" onClick={() => navigate("/")}>
        回到主页
      </button>
      <iframe className="qa-frame" src={src} title="智能问答" />
    </div>
  );
}
