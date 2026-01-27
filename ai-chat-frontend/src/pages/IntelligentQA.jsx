import React, { useEffect, useMemo, useRef, useState } from "react";
import { getApiBase } from "../services/api.js";

export default function IntelligentQA() {
  const [key, setKey] = useState(0);
  const src = useMemo(() => "/qa/chat.html", []);
  const wrapRef = useRef(null);

  useEffect(() => {
    const base = getApiBase();
    if (base) {
      localStorage.setItem("API_BASE", base);
    }
  }, []);

  useEffect(() => {
    function updateHeight() {
      const el = wrapRef.current;
      if (!el) return;
      const rect = el.getBoundingClientRect();
      const available = Math.max(320, window.innerHeight - rect.top - 16);
      el.style.height = `${available}px`;
    }
    updateHeight();
    window.addEventListener("resize", updateHeight);
    return () => window.removeEventListener("resize", updateHeight);
  }, []);

  return (
    <div className="page qa-page">
      <div className="qa-frame-wrap" ref={wrapRef}>
        <iframe key={key} className="qa-frame" src={src} title="智能问答" />
      </div>
    </div>
  );
}
