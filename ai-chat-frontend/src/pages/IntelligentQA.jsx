import React, { useEffect, useMemo } from "react";
import { getApiBase } from "../services/api.js";

export default function IntelligentQA() {
  const src = useMemo(() => "/qa/chat.html", []);

  useEffect(() => {
    const base = getApiBase();
    if (base) {
      localStorage.setItem("API_BASE", base);
    }
  }, []);

  return (
    <div className="page qa-page">
      <iframe className="qa-frame" src={src} title="智能问答" />
    </div>
  );
}
