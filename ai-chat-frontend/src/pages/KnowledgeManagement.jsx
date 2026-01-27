import React, { useEffect, useMemo, useRef, useState } from "react";
import { apiFetch, getApiBase } from "../services/api.js";

export default function KnowledgeManagement() {
  const fileRef = useRef(null);
  const [title, setTitle] = useState("");
  const [loading, setLoading] = useState(false);
  const [documents, setDocuments] = useState([]);
  const [error, setError] = useState("");
  const [summaryModal, setSummaryModal] = useState({ visible: false, content: "", loading: false });
  const apiBase = useMemo(() => getApiBase(), []);

  async function handleSummary(doc, e) {
    if (e) e.stopPropagation();
    setSummaryModal({ visible: true, content: "", loading: true });
    try {
      const resp = await apiFetch(`/api/knowledge/documents/${doc.id}/summary`);
      if (!resp.ok) {
        throw new Error("获取摘要失败");
      }
      const data = await resp.json();
      setSummaryModal({ visible: true, content: data.summary, loading: false });
    } catch (e) {
      setSummaryModal({ visible: true, content: "生成摘要失败: " + e.message, loading: false });
    }
  }

  async function loadDocs() {
    setError("");
    try {
      const resp = await apiFetch("/api/knowledge/documents", { method: "GET" });
      const data = await resp.json().catch(() => []);
      if (!resp.ok) {
        throw new Error(data.message || "加载失败");
      }
      setDocuments(Array.isArray(data) ? data : []);
    } catch (e) {
      setError(e.message || "加载失败");
    }
  }

  useEffect(() => {
    loadDocs();
  }, []);

  async function upload() {
    const file = fileRef.current?.files?.[0];
    if (!file) {
      setError("请选择 PDF 文件");
      return;
    }
    setLoading(true);
    setError("");
    try {
      const form = new FormData();
      form.append("file", file);
      if (title.trim()) {
        form.append("title", title.trim());
      }

      const token = localStorage.getItem("token");
      const resp = await fetch(`${apiBase}/api/knowledge/documents/upload`, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${token}`
        },
        body: form
      });
      const data = await resp.json().catch(() => ({}));
      if (!resp.ok) {
        throw new Error(data.message || "上传失败");
      }
      setTitle("");
      if (fileRef.current) fileRef.current.value = "";
      await loadDocs();
    } catch (e) {
      setError(e.message || "上传失败");
    } finally {
      setLoading(false);
    }
  }

  async function removeDoc(id) {
    if (!confirm("确定要删除该文档吗？")) return;
    setError("");
    try {
      const resp = await apiFetch(`/api/knowledge/documents/${id}`, { method: "DELETE" });
      if (!resp.ok) {
        const data = await resp.json().catch(() => ({}));
        throw new Error(data.message || "删除失败");
      }
      await loadDocs();
    } catch (e) {
      setError(e.message || "删除失败");
    }
  }

  return (
    <div className="page">
      <h1 className="page-title">知识管理</h1>
      <div className="panel">
        <div className="panel-title">上传教材 / 笔记（PDF）</div>
        <div className="panel-desc">
          上传后将自动解析并写入向量库（Chroma），用于题目生成与错题复习推荐。
        </div>
        <div className="form-row">
          <input
            className="text-input"
            placeholder="可选：文档标题（不填则使用文件名）"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
          <input ref={fileRef} className="file-input" type="file" accept="application/pdf" />
          <button className="primary-btn" type="button" disabled={loading} onClick={upload}>
            {loading ? "处理中..." : "上传并入库"}
          </button>
        </div>
        {error && <div className="msg error">{error}</div>}
      </div>

      <div className="panel">
        <div className="panel-title">我的知识库</div>
        <div className="table">
          <div className="thead">
            <div>标题</div>
            <div>状态</div>
            <div>切分</div>
            <div>更新时间</div>
            <div></div>
          </div>
          {documents.length === 0 ? (
            <div className="trow empty">暂无文档</div>
          ) : (
            documents.map((d) => (
              <div className="trow" key={d.id}>
                <div className="mono" onClick={(e) => handleSummary(d, e)} style={{cursor: 'pointer', color: '#667eea', textDecoration: 'underline'}} title="点击查看摘要">
                  {d.title}
                </div>
                <div>{d.status}</div>
                <div>{d.segmentCount}</div>
                <div className="mono">{d.updatedAt || "-"}</div>
                <div>
                  <button className="danger-btn" type="button" onClick={() => removeDoc(d.id)}>
                    删除
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      </div>

      {summaryModal.visible && (
        <div style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000
        }} onClick={() => setSummaryModal({ ...summaryModal, visible: false })}>
          <div style={{
            background: 'white', padding: '24px', borderRadius: '16px', maxWidth: '600px', width: '90%', maxHeight: '80vh', overflowY: 'auto'
          }} onClick={e => e.stopPropagation()}>
            <h3 style={{marginTop: 0, marginBottom: '16px', fontSize: '20px', fontWeight: 'bold'}}>文档摘要</h3>
            {summaryModal.loading ? (
              <div style={{padding: '20px', textAlign: 'center', color: '#666'}}>正在生成摘要，请稍候...</div>
            ) : (
              <div style={{lineHeight: '1.6', whiteSpace: 'pre-wrap', color: '#333'}}>{summaryModal.content}</div>
            )}
            <div style={{textAlign: 'right', marginTop: '20px'}}>
              <button className="primary-btn" onClick={() => setSummaryModal({ ...summaryModal, visible: false })}>关闭</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
