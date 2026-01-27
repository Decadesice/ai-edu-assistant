import React, { useEffect, useMemo, useState } from "react";
import { apiFetch } from "../services/api.js";

export default function QuestionGeneration() {
  const [documents, setDocuments] = useState([]);
  const [documentId, setDocumentId] = useState("");
  const [chapterHint, setChapterHint] = useState("");
  const [count, setCount] = useState(5);
  const [types, setTypes] = useState(["single"]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [questions, setQuestions] = useState([]);
  const [answers, setAnswers] = useState({});
  const [results, setResults] = useState({});

  async function loadDocs() {
    try {
      const resp = await apiFetch("/api/knowledge/documents", { method: "GET" });
      const data = await resp.json().catch(() => []);
      if (resp.ok) {
        setDocuments(Array.isArray(data) ? data : []);
      }
    } catch {
    }
  }

  useEffect(() => {
    loadDocs();
  }, []);

  const selectedDoc = useMemo(() => documents.find((d) => String(d.id) === String(documentId)), [documents, documentId]);

  function toggleType(t) {
    setTypes((prev) => {
      const set = new Set(prev);
      if (set.has(t)) set.delete(t);
      else set.add(t);
      const out = Array.from(set);
      return out.length === 0 ? ["single"] : out;
    });
  }

  function normalizeMultipleChosen(v) {
    const parts = String(v || "")
      .toUpperCase()
      .split(/[^A-Z]/)
      .filter(Boolean)
      .sort();
    return parts.join(",");
  }

  async function generate() {
    if (!documentId) {
      setError("请选择一个文档");
      return;
    }
    setLoading(true);
    setError("");
    setQuestions([]);
    setAnswers({});
    setResults({});
    try {
      const resp = await apiFetch("/api/questions/generate", {
        method: "POST",
        body: JSON.stringify({
          documentId: Number(documentId),
          chapterHint: chapterHint.trim(),
          count: Number(count || 5),
          model: "glm-4.6v-Flash",
          types
        })
      });
      const data = await resp.json().catch(() => ({}));
      if (!resp.ok) {
        throw new Error(data.message || "生成失败");
      }
      setQuestions(Array.isArray(data) ? data : []);
    } catch (e) {
      setError(e.message || "生成失败");
    } finally {
      setLoading(false);
    }
  }

  async function submit(q) {
    let chosen = answers[q.id];
    const type = (q.type || "single").toLowerCase();
    if (type === "multiple") {
      chosen = normalizeMultipleChosen(Array.isArray(chosen) ? chosen.join(",") : chosen);
    }
    if (!chosen) {
      setError(type === "short" ? "请输入你的答案" : "请选择一个选项");
      return;
    }
    setError("");
    try {
      const resp = await apiFetch(`/api/questions/${q.id}/attempt`, {
        method: "POST",
        body: JSON.stringify({ chosen })
      });
      const data = await resp.json().catch(() => ({}));
      if (!resp.ok) {
        throw new Error(data.message || "提交失败");
      }
      setResults((prev) => ({
        ...prev,
        [q.id]: { correct: !!data.correct, chosen }
      }));
    } catch (e) {
      setError(e.message || "提交失败");
    }
  }

  return (
    <div className="page">
      <h1 className="page-title">题目生成</h1>
      <div className="panel">
        <div className="panel-title">按章节生成题目</div>
        <div className="panel-desc">
          从知识库材料中检索相关片段，调用大模型生成题目并附解析，可选择题型。
        </div>
        <div className="form-row">
          <select className="text-input" value={documentId} onChange={(e) => setDocumentId(e.target.value)}>
            <option value="">选择文档…</option>
            {documents.map((d) => (
              <option key={d.id} value={d.id}>
                {d.title}
              </option>
            ))}
          </select>
          <input
            className="text-input"
            placeholder="例如：第三章 逻辑推理 / 马克思主义基本原理"
            value={chapterHint}
            onChange={(e) => setChapterHint(e.target.value)}
          />
          <input
            className="text-input"
            type="number"
            min="1"
            max="10"
            value={count}
            onChange={(e) => setCount(e.target.value)}
            style={{ width: 120 }}
          />
          <div className="type-pills">
            <button
              className={`pill ${types.includes("single") ? "active" : ""}`}
              type="button"
              onClick={() => toggleType("single")}
            >
              单选题
            </button>
            <button
              className={`pill ${types.includes("multiple") ? "active" : ""}`}
              type="button"
              onClick={() => toggleType("multiple")}
            >
              多选题
            </button>
            <button
              className={`pill ${types.includes("judgment") ? "active" : ""}`}
              type="button"
              onClick={() => toggleType("judgment")}
            >
              判断题
            </button>
            <button
              className={`pill ${types.includes("short") ? "active" : ""}`}
              type="button"
              onClick={() => toggleType("short")}
            >
              解答题
            </button>
          </div>
          <button className="primary-btn" type="button" disabled={loading} onClick={generate}>
            {loading ? "生成中..." : "生成题目"}
          </button>
        </div>
        {selectedDoc && selectedDoc.status !== "READY" && (
          <div className="msg error">当前文档状态为 {selectedDoc.status}，建议等待入库完成后再生成。</div>
        )}
        {error && <div className="msg error">{error}</div>}
      </div>

      <div className="panel">
        <div className="panel-title">生成结果</div>
        {questions.length === 0 ? (
          <div className="placeholder">暂无题目，请先生成</div>
        ) : (
          <div className="q-list">
            {questions.map((q, idx) => {
              const result = results[q.id];
              const type = (q.type || "single").toLowerCase();
              return (
                <div className="q-card" key={q.id}>
                  <div className="q-head">
                    <div className="q-title">
                      {idx + 1}. {q.topic}
                    </div>
                    {result && (
                      <div className={`badge ${result.correct ? "ok" : "bad"}`}>
                        {result.correct ? "正确" : "错误"}
                      </div>
                    )}
                  </div>
                  <div className="q-stem">{q.stem}</div>
                  {type === "short" ? (
                    <div className="short-answer">
                      <textarea
                        className="text-input"
                        placeholder="请输入你的解答…"
                        value={answers[q.id] || ""}
                        onChange={(e) => setAnswers((prev) => ({ ...prev, [q.id]: e.target.value }))}
                        disabled={!!result}
                        rows={4}
                        style={{ width: "100%", resize: "vertical" }}
                      />
                    </div>
                  ) : (
                    <div className="q-options">
                      {(q.options || []).map((op) => (
                        <label className="q-option" key={op.key}>
                          <input
                            type={type === "multiple" ? "checkbox" : "radio"}
                            name={`q-${q.id}`}
                            value={op.key}
                            checked={
                              type === "multiple"
                                ? Array.isArray(answers[q.id]) && answers[q.id].includes(op.key)
                                : answers[q.id] === op.key
                            }
                            onChange={() => {
                              if (type === "multiple") {
                                setAnswers((prev) => {
                                  const cur = Array.isArray(prev[q.id]) ? prev[q.id] : [];
                                  const next = cur.includes(op.key)
                                    ? cur.filter((x) => x !== op.key)
                                    : [...cur, op.key];
                                  return { ...prev, [q.id]: next };
                                });
                              } else {
                                setAnswers((prev) => ({ ...prev, [q.id]: op.key }));
                              }
                            }}
                            disabled={!!result}
                          />
                          <span className="mono">{op.key}.</span>
                          <span>{op.text}</span>
                        </label>
                      ))}
                    </div>
                  )}
                  <div className="q-actions">
                    <button className="primary-btn" type="button" disabled={!!result} onClick={() => submit(q)}>
                      提交答案
                    </button>
                  </div>
                  {result && (
                    <div className="q-explain">
                      <div className="mono">正确答案：{q.answer}</div>
                      <div className="q-explain-text">{q.explanation}</div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
