import React, { useEffect, useMemo, useState } from "react";
import { apiFetch } from "../services/api.js";

export default function LearningStatistics() {
  const [overview, setOverview] = useState(null);
  const [wrongbook, setWrongbook] = useState([]);
  const [groups, setGroups] = useState([]);
  const [activeGroup, setActiveGroup] = useState("all"); // all | ungrouped | group:<id>
  const [expanded, setExpanded] = useState({});
  const [showGroupModal, setShowGroupModal] = useState(false);
  const [newGroupName, setNewGroupName] = useState("");
  const [error, setError] = useState("");

  function buildOptionTextMap(q) {
    const map = {};
    const list = Array.isArray(q?.options) ? q.options : [];
    for (const o of list) {
      const k = String(o?.key || "").trim().toUpperCase();
      const text = String(o?.text || "").trim();
      if (k) {
        map[k] = text;
      }
    }
    return map;
  }

  function formatAnswerWithText(answer, q) {
    const a = String(answer || "").trim();
    const options = Array.isArray(q?.options) ? q.options : [];
    if (!a) return "-";
    if (options.length === 0) return a;
    const map = buildOptionTextMap(q);
    const parts = a
      .toUpperCase()
      .split(/[^A-Z]/)
      .filter(Boolean);
    if (parts.length === 0) return a;
    return parts
      .map((k) => (map[k] ? `${k}.${map[k]}` : k))
      .join("；");
  }

  const wrongbookQuery = useMemo(() => {
    if (activeGroup === "all") return "";
    if (activeGroup === "ungrouped") return "?ungrouped=true";
    if (activeGroup.startsWith("group:")) {
      const id = activeGroup.slice("group:".length);
      return `?groupId=${encodeURIComponent(id)}`;
    }
    return "";
  }, [activeGroup]);

  async function load() {
    setError("");
    try {
      const [oResp, wResp] = await Promise.all([
        apiFetch("/api/stats/overview", { method: "GET" }),
        apiFetch(`/api/stats/wrongbook${wrongbookQuery}`, { method: "GET" })
      ]);
      const o = await oResp.json().catch(() => ({}));
      const w = await wResp.json().catch(() => []);
      if (!oResp.ok) {
        throw new Error(o.message || "加载失败");
      }
      if (!wResp.ok) {
        throw new Error((w && w.message) || "加载失败");
      }
      setOverview(o);
      setWrongbook(Array.isArray(w) ? w : []);
    } catch (e) {
      setError(e.message || "加载失败");
    }
  }

  async function loadGroups() {
    try {
      const resp = await apiFetch("/api/wrongbook/groups", { method: "GET" });
      const data = await resp.json().catch(() => []);
      if (resp.ok) {
        setGroups(Array.isArray(data) ? data : []);
      }
    } catch {
    }
  }

  async function createGroupName(name) {
    const n = String(name || "").trim();
    if (!n) return;
    setError("");
    try {
      const resp = await apiFetch("/api/wrongbook/groups", {
        method: "POST",
        body: JSON.stringify({ name: n })
      });
      const data = await resp.json().catch(() => ({}));
      if (!resp.ok) throw new Error(data.message || "创建失败");
      await loadGroups();
      await load();
    } catch (e) {
      setError(e.message || "创建失败");
    }
  }

  async function createGroup() {
    const name = prompt("请输入分组名称");
    if (!name) return;
    await createGroupName(name);
  }

  async function renameGroup(g) {
    const name = prompt("请输入新的分组名称", g.name || "");
    if (!name) return;
    setError("");
    try {
      const resp = await apiFetch(`/api/wrongbook/groups/${g.id}`, {
        method: "PUT",
        body: JSON.stringify({ name })
      });
      const data = await resp.json().catch(() => ({}));
      if (!resp.ok) throw new Error(data.message || "修改失败");
      await loadGroups();
    } catch (e) {
      setError(e.message || "修改失败");
    }
  }

  async function deleteGroup(g) {
    if (!confirm("删除分组后，该分组下的错题会变为未分组。确定删除吗？")) return;
    setError("");
    try {
      const resp = await apiFetch(`/api/wrongbook/groups/${g.id}`, { method: "DELETE" });
      const data = await resp.json().catch(() => ({}));
      if (!resp.ok) throw new Error(data.message || "删除失败");
      if (activeGroup === `group:${g.id}`) setActiveGroup("ungrouped");
      await loadGroups();
      await load();
    } catch (e) {
      setError(e.message || "删除失败");
    }
  }

  async function assignGroup(questionId, groupId) {
    setError("");
    try {
      const resp = await apiFetch(`/api/wrongbook/questions/${questionId}/group`, {
        method: "PUT",
        body: JSON.stringify({ groupId })
      });
      const data = await resp.json().catch(() => ({}));
      if (!resp.ok) throw new Error(data.message || "分组失败");
      await load();
    } catch (e) {
      setError(e.message || "分组失败");
    }
  }

  useEffect(() => {
    loadGroups();
  }, []);

  useEffect(() => {
    load();
  }, [wrongbookQuery]);

  return (
    <div className="page">
      <h1 className="page-title">学习统计</h1>
      {error && <div className="msg error">{error}</div>}

      <div className="stats-compact">
        <div className="stat-chip">总做题：{overview ? overview.totalAttempts : "-"}</div>
        <div className="stat-chip">正确：{overview ? overview.correctAttempts : "-"}</div>
        <div className="stat-chip">错误：{overview ? overview.wrongAttempts : "-"}</div>
        <div className="stat-chip">
          正确率：{overview ? `${Math.round((overview.accuracy || 0) * 100)}%` : "-"}
        </div>
      </div>

      <div className="panel">
        <div className="panel-title">错题本</div>

        <div className="wrongbook-topbar">
          <div className="wrongbook-filter">
            <span className="mono">分组：</span>
            <select
              className="text-input"
              value={activeGroup}
              onChange={(e) => setActiveGroup(e.target.value)}
              style={{ maxWidth: 320 }}
            >
              <option value="all">全部错题</option>
              <option value="ungrouped">未分组</option>
              {groups.map((g) => (
                <option key={g.id} value={`group:${g.id}`}>
                  {g.name}
                </option>
              ))}
            </select>
          </div>
          <div className="wrongbook-actions">
            <button className="tiny-btn" type="button" onClick={() => setShowGroupModal(true)}>
              分组管理
            </button>
          </div>
        </div>

        {wrongbook.length === 0 ? (
          <div className="placeholder">暂无错题</div>
        ) : (
          <div className="wrong-list">
            {wrongbook.map((item, idx) => {
              const q = item.question;
              const snippets = item.snippets || [];
              const isOpen = !!expanded[q.id];
              const currentGroupId = item.groupId ?? null;
              const chosenText = formatAnswerWithText(item.chosen, q);
              const answerText = formatAnswerWithText(q.answer, q);
              return (
                <div className="wrong-item" key={q.id || idx}>
                  <button
                    className="wrong-summary"
                    type="button"
                    onClick={() => setExpanded((prev) => ({ ...prev, [q.id]: !prev[q.id] }))}
                  >
                    <div className="wrong-stem">{q.stem}</div>
                    <div className="wrong-meta mono">
                      你的答案：{chosenText}；正确答案：{answerText}
                    </div>
                  </button>

                  {isOpen && (
                    <div className="wrong-detail">
                      <div className="wrong-detail-row">
                        <div className="mono">题型：{q.type || "-"}</div>
                        <div className="group-select">
                          <span className="mono">分组：</span>
                          <select
                            className="text-input"
                            value={currentGroupId == null ? "" : String(currentGroupId)}
                            onChange={(e) => {
                              const v = e.target.value;
                              assignGroup(q.id, v ? Number(v) : null);
                            }}
                          >
                            <option value="">未分组</option>
                            {groups.map((g) => (
                              <option key={g.id} value={g.id}>
                                {g.name}
                              </option>
                            ))}
                          </select>
                        </div>
                      </div>

                      <div className="mono">主题：{q.topic || "-"}</div>
                      <div className="mono">你的答案：{chosenText}</div>
                      <div className="mono">正确答案：{answerText}</div>

                      {Array.isArray(q.options) && q.options.length > 0 && (
                        <div className="q-options" style={{ marginTop: 10 }}>
                          {q.options.map((o) => (
                            <div className="q-option" key={o.key}>
                              <div className="mono" style={{ width: 28 }}>
                                {o.key}
                              </div>
                              <div style={{ lineHeight: 1.7 }}>{o.text}</div>
                            </div>
                          ))}
                        </div>
                      )}

                      <div className="q-explain-text">{q.explanation}</div>
                      <div className="snippets">
                        <div className="snip-title">原文推荐</div>
                        {snippets.length === 0 ? (
                          <div className="snip">暂无推荐片段</div>
                        ) : (
                          snippets.map((s, i) => (
                            <div className="snip" key={i}>
                              {s.content}
                            </div>
                          ))
                        )}
                      </div>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>

      {showGroupModal && (
        <div
          className="modal-backdrop"
          role="presentation"
          onClick={() => {
            setShowGroupModal(false);
            setNewGroupName("");
          }}
        >
          <div
            className="modal"
            role="dialog"
            aria-modal="true"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="modal-head">
              <div className="modal-title">分组管理</div>
              <button
                className="tiny-btn"
                type="button"
                onClick={() => {
                  setShowGroupModal(false);
                  setNewGroupName("");
                }}
              >
                关闭
              </button>
            </div>
            <div className="modal-body">
              <div className="modal-create">
                <input
                  className="text-input"
                  placeholder="新分组名称"
                  value={newGroupName}
                  onChange={(e) => setNewGroupName(e.target.value)}
                />
                <button
                  className="primary-btn"
                  type="button"
                  onClick={async () => {
                    await createGroupName(newGroupName);
                    setNewGroupName("");
                  }}
                >
                  新建
                </button>
              </div>

              {groups.length === 0 ? (
                <div className="placeholder">暂无分组</div>
              ) : (
                <div className="modal-group-list">
                  {groups.map((g) => (
                    <div className="modal-group-row" key={g.id}>
                      <div className="modal-group-name" title={g.name}>
                        {g.name}
                      </div>
                      <div className="modal-group-actions">
                        <button className="tiny-btn" type="button" onClick={() => renameGroup(g)}>
                          改名
                        </button>
                        <button className="tiny-btn danger" type="button" onClick={() => deleteGroup(g)}>
                          删除
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
