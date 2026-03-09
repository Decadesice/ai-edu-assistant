import React from "react";
import { useNavigate } from "react-router-dom";

export default function Home() {
  const navigate = useNavigate();

  return (
    <div className="page home-page">
      <section className="home-hero">
        <div className="home-hero-inner">
          <div className="home-kicker">AI 学习工作台</div>
          <h1 className="home-title">个性化 AI 学习伴侣</h1>
          <div className="home-subtitle">从资料解析到问答与练习生成，一站式完成学习闭环。</div>
          <div className="home-cta">
            <button type="button" className="primary-btn" onClick={() => navigate("/knowledge")}>
              开始上传资料
            </button>
            <button type="button" className="ghost-btn home-ghost" onClick={() => navigate("/qa")}>
              进入智能问答
            </button>
          </div>
        </div>
      </section>

      <div className="home-grid">
        <section className="home-features home-panel">
          <div className="home-panel-title">系统功能</div>
          <div className="cards">
            <button type="button" className="card card--knowledge" onClick={() => navigate("/knowledge")}>
              <div className="card-head">
                <div className="card-icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path
                      d="M8 7h8M8 11h8M8 15h5M7 3h8l4 4v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                </div>
                <div className="card-title">知识管理</div>
              </div>
              <div className="card-desc">PDF 文档解析、知识点提取与结构化管理</div>
            </button>

            <button type="button" className="card card--qa" onClick={() => navigate("/qa")}>
              <div className="card-head">
                <div className="card-icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path
                      d="M7 8h10M7 12h6M12 20l-3-3H7a4 4 0 0 1-4-4V8a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4v5a4 4 0 0 1-4 4h-2l-3 3Z"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                </div>
                <div className="card-title">智能问答</div>
              </div>
              <div className="card-desc">基于对话上下文的智能问答，支持图片分析</div>
            </button>

            <button type="button" className="card card--gen" onClick={() => navigate("/generate")}>
              <div className="card-head">
                <div className="card-icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path
                      d="M12 2l1.2 4.2L17.4 7.4 13.2 8.6 12 12.8 10.8 8.6 6.6 7.4l4.2-1.2L12 2Z"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinejoin="round"
                    />
                    <path
                      d="M19 12l.8 2.8L22.6 16l-2.8.8L19 19.6l-.8-2.8L15.4 16l2.8-1.2L19 12Z"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinejoin="round"
                    />
                    <path
                      d="M5 13l.9 3.1L9 17l-3.1.9L5 21l-.9-3.1L1 17l3.1-.9L5 13Z"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinejoin="round"
                    />
                  </svg>
                </div>
                <div className="card-title">题目生成</div>
              </div>
              <div className="card-desc">AI 驱动的知识点题目生成</div>
            </button>

            <button type="button" className="card card--stats" onClick={() => navigate("/stats")}>
              <div className="card-head">
                <div className="card-icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24" width="20" height="20" fill="none" xmlns="http://www.w3.org/2000/svg">
                    <path
                      d="M4 19V5M4 19h16M8 16v-6M12 16V8M16 16v-3"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                  </svg>
                </div>
                <div className="card-title">学习统计</div>
              </div>
              <div className="card-desc">学习进度跟踪与复习建议</div>
            </button>
          </div>
        </section>

        <aside className="quickstart home-quickstart">
          <div className="quickstart-title">快速开始</div>
          <ol className="steps">
            <li>上传学习资料（PDF 格式）</li>
            <li>系统自动提取知识点</li>
            <li>生成相关题目进行学习</li>
            <li>查看学习统计与复习建议</li>
          </ol>
        </aside>
      </div>
    </div>
  );
}
