import React from "react";
import { useNavigate } from "react-router-dom";

export default function Home() {
  const navigate = useNavigate();

  return (
    <div className="page">
      <h1 className="page-title">系统功能</h1>

      <div className="cards">
        <button type="button" className="card" onClick={() => navigate("/knowledge")}>
          <div className="card-title">知识管理</div>
          <div className="card-desc">PDF文档解析、知识点提取与结构化管理</div>
        </button>
        <button type="button" className="card" onClick={() => navigate("/qa")}>
          <div className="card-title">智能问答</div>
          <div className="card-desc">基于对话上下文的智能问答，支持图片分析</div>
        </button>
        <button type="button" className="card" onClick={() => navigate("/generate")}>
          <div className="card-title">题目生成</div>
          <div className="card-desc">AI驱动的知识点题目生成</div>
        </button>
        <button type="button" className="card" onClick={() => navigate("/stats")}>
          <div className="card-title">学习统计</div>
          <div className="card-desc">学习进度跟踪与复习建议</div>
        </button>
      </div>

      <div className="quickstart">
        <h2 className="quickstart-title">快速开始</h2>
        <ol className="steps">
          <li>上传学习资料（PDF格式）</li>
          <li>系统自动提取知识点</li>
          <li>生成相关题目进行学习</li>
          <li>查看学习统计与复习建议</li>
        </ol>
      </div>
    </div>
  );
}

