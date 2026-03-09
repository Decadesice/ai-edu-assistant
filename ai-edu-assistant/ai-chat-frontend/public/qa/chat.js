function isLocalHost(hostname) {
  return hostname === "localhost" || hostname === "127.0.0.1" || hostname === "::1";
}

const API_BASE = (window.API_BASE || (isLocalHost(window.location.hostname) ? "http://localhost:8081" : "")).replace(/\/$/, "");

let sessionId = null;
let currentImageBase64 = null;
let currentModel = "qwen3.5:0.8b";
let token = localStorage.getItem('token');
let userId = localStorage.getItem('userId');
let username = localStorage.getItem('username');

const chatContainer = document.getElementById("chatContainer");
const messageInput = document.getElementById("messageInput");
const sendBtn = document.getElementById("sendBtn");
const resetBtn = document.getElementById("resetBtn");
const imageInput = document.getElementById("imageInput");
const imageLabel = document.getElementById("imageLabel");
const imagePreview = document.getElementById("imagePreview");
const removeImageBtn = document.getElementById("removeImageBtn");
const typingIndicator = document.getElementById("typingIndicator");
const errorMessage = document.getElementById("errorMessage");
const modelSelect = document.getElementById("modelSelect");
const userInfo = document.getElementById("userInfo");
const logoutBtn = document.getElementById("logoutBtn");
const newChatBtn = document.getElementById("newChatBtn");
const conversationList = document.getElementById("conversationList");
const conversationTopic = document.getElementById("conversationTopic");
const backBtn = document.getElementById("backBtn");
const desktopBackBtn = document.getElementById("desktopBackBtn");
const headerBackBtn = document.getElementById("headerBackBtn");
let conversationMetaBySessionId = new Map();

let autoScrollEnabled = true;
let lastSendPayload = null;
let activeAbortController = null;

function isNearBottom() {
  if (!chatContainer) return true;
  const threshold = 80;
  return chatContainer.scrollHeight - chatContainer.scrollTop - chatContainer.clientHeight < threshold;
}

function scrollIfNeeded(force = false) {
  if (!chatContainer) return;
  if (force || autoScrollEnabled) {
    chatContainer.scrollTop = chatContainer.scrollHeight;
  }
}

if (chatContainer) {
  chatContainer.addEventListener("scroll", () => {
    autoScrollEnabled = isNearBottom();
  }, { passive: true });
}

function gotoAppHash(hashPath) {
  const target = hashPath.startsWith("/#") ? hashPath : "/#" + hashPath;
  if (window.top && window.top !== window.self) {
    window.top.location.href = target;
  } else {
    window.location.href = target;
  }
}

if (!token) {
  gotoAppHash("/login");
}

function goHome() {
  gotoAppHash("/");
}

if (backBtn) {
  backBtn.addEventListener("click", goHome);
}

if (desktopBackBtn) {
  desktopBackBtn.addEventListener("click", goHome);
}

if (headerBackBtn) {
  headerBackBtn.addEventListener("click", goHome);
}

if (modelSelect) {
  modelSelect.value = currentModel;
}

if (userInfo) {
  userInfo.textContent = `欢迎, ${username}`;
}

async function fetchWithAuth(url, options = {}) {
  const fullUrl = url.startsWith("http") ? url : API_BASE + url;
  const headers = Object.assign(
    {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${token}`
    },
    options.headers || {}
  );
  const response = await fetch(fullUrl, Object.assign({}, options, { headers }));
  return response;
}

function formatConversationTitle(title) {
  const text = (title || "").trim().replace(/\s+/g, " ");
  if (!text) return "新对话";
  if (text.length <= 16) return text;
  return text.slice(0, 16) + "...";
}

function formatConversationTopic(title) {
  const text = (title || "").trim().replace(/\s+/g, " ");
  if (!text) return "新对话";
  if (text.length <= 24) return text;
  return text.slice(0, 24) + "...";
}

function setConversationTopicText(title) {
  if (!conversationTopic) return;
  conversationTopic.textContent = formatConversationTopic(title);
}

function setActiveConversationItem(activeSessionId) {
  if (!conversationList) return;
  const items = conversationList.querySelectorAll(".conversation-item");
  items.forEach((el) => {
    if (el.dataset.sessionId === activeSessionId) {
      el.classList.add("active");
    } else {
      el.classList.remove("active");
    }
  });
}

async function loadConversationList() {
  if (!conversationList) return;
  try {
    const resp = await fetchWithAuth("/api/unified/conversation/list/detail", { method: "GET" });
    if (!resp.ok) return;
    const data = await resp.json();
    conversationList.innerHTML = "";
    conversationMetaBySessionId = new Map();

    for (const conv of data) {
      conversationMetaBySessionId.set(conv.sessionId, conv);
      const item = document.createElement("div");
      item.className = "conversation-item";
      item.dataset.sessionId = conv.sessionId;

      const icon = document.createElement("div");
      icon.className = "conversation-icon";
      icon.textContent = "💬";

      const title = document.createElement("div");
      title.className = "conversation-title";
      title.textContent = formatConversationTitle(conv.title);

      const actions = document.createElement("div");
      actions.className = "conversation-actions";

      const deleteBtn = document.createElement("button");
      deleteBtn.className = "conversation-action-btn danger";
      deleteBtn.type = "button";
      deleteBtn.title = "删除对话";
      deleteBtn.innerHTML = "<svg width=16 height=16 viewBox='0 0 24 24' fill='none'><path d='M3 6H21' stroke='currentColor' stroke-width='2' stroke-linecap='round'/><path d='M8 6V4C8 3.44772 8.44772 3 9 3H15C15.5523 3 16 3.44772 16 4V6' stroke='currentColor' stroke-width='2' stroke-linecap='round'/><path d='M6 6L7 21H17L18 6' stroke='currentColor' stroke-width='2' stroke-linecap='round'/><path d='M10 11V17' stroke='currentColor' stroke-width='2' stroke-linecap='round'/><path d='M14 11V17' stroke='currentColor' stroke-width='2' stroke-linecap='round'/></svg>";
      deleteBtn.addEventListener("click", async (e) => {
        e.stopPropagation();
        if (!confirm("确定要删除该对话吗？")) return;
        try {
          const delResp = await fetchWithAuth(`/api/unified/conversation/${encodeURIComponent(conv.sessionId)}`, { method: "DELETE" });
          if (!delResp.ok) {
            const err = await delResp.json().catch(() => ({}));
            throw new Error(err.message || "删除失败");
          }

          if (sessionId === conv.sessionId) {
            sessionId = null;
            clearChatToWelcome();
            setConversationTopicText("新对话");
          }
          await loadConversationList();
        } catch (err) {
          showError(err.message || "删除失败");
        }
      });

      actions.appendChild(deleteBtn);

      item.appendChild(icon);
      item.appendChild(title);
      item.appendChild(actions);
      item.addEventListener("click", async () => {
        setConversationTopicText(conv.title);
        await openConversation(conv.sessionId);
      });

      conversationList.appendChild(item);
    }

    setActiveConversationItem(sessionId);
    if (sessionId && conversationMetaBySessionId.has(sessionId)) {
      setConversationTopicText(conversationMetaBySessionId.get(sessionId).title);
    } else {
      setConversationTopicText("新对话");
    }
  } catch (e) {
  }
}

function clearChatToWelcome() {
  chatContainer.innerHTML = `
    <div class="message-wrapper ai-message">
      <div class="avatar ai">舟</div>
      <div class="message-content">
        你好！我是题舟。我可以帮你解答问题、分析图片，或者只是聊聊天。
      </div>
    </div>
    <div class="typing-indicator" id="typingIndicator" style="display: none; margin-left: 80px;">
       <div class="typing"><span></span><span></span><span></span></div>
    </div>
  `;
}

async function openConversation(targetSessionId) {
  sessionId = targetSessionId;
  setActiveConversationItem(sessionId);
  if (conversationMetaBySessionId.has(sessionId)) {
    setConversationTopicText(conversationMetaBySessionId.get(sessionId).title);
  } else {
    setConversationTopicText("新对话");
  }
  clearChatToWelcome();

  try {
    const resp = await fetchWithAuth(`/api/unified/conversation/${encodeURIComponent(sessionId)}`, { method: "GET" });
    if (!resp.ok) {
      return;
    }
    const messages = await resp.json();
    chatContainer.innerHTML = "";

    for (const m of messages) {
      if (m.images && m.images.length > 0) {
        let imgSrc = m.images[0];
        if (imgSrc && !imgSrc.startsWith("data:")) {
          imgSrc = "data:image/jpeg;base64," + imgSrc;
        }
        addMessage(m.role === "assistant" ? "assistant" : "user", imgSrc, true);
      }
      if (m.content) {
        addMessage(m.role === "assistant" ? "assistant" : "user", m.content, false);
      }
    }

    const typing = document.createElement("div");
    typing.className = "typing-indicator";
    typing.id = "typingIndicator";
    typing.style.display = "none";
    typing.style.marginLeft = "80px";
    typing.innerHTML = '<div class="typing"><span></span><span></span><span></span></div>';
    chatContainer.appendChild(typing);
  } catch (e) {
  }
}

async function createNewConversation(silent = false) {
  const resp = await fetchWithAuth("/api/unified/conversation/new", {
    method: "POST",
    body: JSON.stringify({ title: "新对话", modelName: currentModel })
  });
  if (!resp.ok) {
    const errorData = await resp.json().catch(() => ({}));
    throw new Error(errorData.message || "创建对话失败");
  }
  const newSessionId = await resp.text();
  sessionId = newSessionId;
  if (!silent) {
    clearChatToWelcome();
  }
  setConversationTopicText("新对话");
  await loadConversationList();
  setActiveConversationItem(sessionId);
}

if (newChatBtn) {
  newChatBtn.addEventListener("click", async () => {
    try {
      await createNewConversation();
    } catch (e) {
      showError(e.message || "创建对话失败");
    }
  });
}

loadConversationList();

function generateSessionId() {
  return (
    "session_" + Date.now() + "_" + Math.random().toString(36).substr(2, 9)
  );
}

function getTypingIndicatorElement() {
  return chatContainer.querySelector("#typingIndicator");
}

function addMessage(role, content, isImage = false, isThinking = false) {
  const messageDiv = document.createElement("div");
  messageDiv.className = `message-wrapper ${role === "user" ? "user-message" : "ai-message"}`;

  const avatar = document.createElement("div");
  avatar.className = role === "user" ? "avatar user" : "avatar ai";
  avatar.textContent = role === "user" ? "U" : "AI";

  const messageContent = document.createElement("div");
  messageContent.className = `message-content${isImage ? " is-image" : ""}${isThinking ? " thinking" : ""}`;

  if (isImage) {
    const img = document.createElement("img");
    img.src = content;
    img.loading = "lazy";
    img.decoding = "async";
    img.addEventListener("click", () => {
      try {
        window.open(content, "_blank", "noopener,noreferrer");
      } catch (e) {
      }
    });
    messageContent.appendChild(img);
  } else if (isThinking) {
    const thinkingContainer = document.createElement("div");
    thinkingContainer.className = "thinking-container";
    thinkingContainer.textContent = content;
    
    const foldButton = document.createElement("button");
    foldButton.className = "fold-button";
    foldButton.innerHTML = "<svg width=16 height=16><path d='M8 4L4 8L8 12' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'/></svg>";
    foldButton.addEventListener("click", function() {
      const content = messageContent.querySelector(".thinking-container");
      if (content.style.display === "none") {
        content.style.display = "block";
        foldButton.innerHTML = "<svg width=16 height=16><path d='M8 4L4 8L8 12' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'/></svg>";
      } else {
        content.style.display = "none";
        foldButton.innerHTML = "<svg width=16 height=16><path d='M4 8L8 12L12 8' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'/></svg>";
      }
    });
    
    messageContent.appendChild(foldButton);
    messageContent.appendChild(thinkingContainer);
  } else {
    messageContent.innerHTML = marked.parse(content);
  }

  messageDiv.appendChild(avatar);
  messageDiv.appendChild(messageContent);
  const typingEl = getTypingIndicatorElement();
  if (typingEl) {
    chatContainer.insertBefore(messageDiv, typingEl);
  } else {
    chatContainer.appendChild(messageDiv);
  }
  scrollIfNeeded();
}

function showTyping() {
  const typingEl = getTypingIndicatorElement();
  if (typingEl) {
    typingEl.style.display = "inline-flex";
  }
  scrollIfNeeded();
  syncComposerHeight();
}

function hideTyping() {
  const typingEl = getTypingIndicatorElement();
  if (typingEl) {
    typingEl.style.display = "none";
  }
  syncComposerHeight();
}

function showError(message) {
  errorMessage.textContent = message;
  errorMessage.style.display = "block";
  syncComposerHeight();
  setTimeout(() => {
    errorMessage.style.display = "none";
    syncComposerHeight();
  }, 5000);
}

if (imageInput) {
imageInput.addEventListener("change", function (e) {
  const file = e.target.files[0];
  if (file) {
    if (!file.type || !file.type.startsWith("image/")) {
      showError("请选择图片文件");
      imageInput.value = "";
      return;
    }
    const maxBytes = 10 * 1024 * 1024;
    if (file.size > maxBytes) {
      showError("图片过大，请选择小于 10MB 的图片");
      imageInput.value = "";
      return;
    }
    const reader = new FileReader();
    reader.onload = function (event) {
      currentImageBase64 = event.target.result;
      if (imagePreview) {
        imagePreview.src = currentImageBase64;
        imagePreview.style.display = "block";
      }
      if (removeImageBtn) {
        removeImageBtn.style.display = "block";
        if (imageLabel) {
          imageLabel.style.display = "none";
        }
      }
      syncComposerHeight();
      scrollChatToBottom();
    };
    reader.readAsDataURL(file);
  }
});
}

if (removeImageBtn) {
  removeImageBtn.addEventListener("click", function () {
    currentImageBase64 = null;
    if (imageInput) {
      imageInput.value = "";
    }
    if (imagePreview) {
      imagePreview.src = "";
      imagePreview.style.display = "none";
    }
    removeImageBtn.style.display = "none";
    if (imageLabel) {
      imageLabel.style.display = "block";
    }
    syncComposerHeight();
  });
}

if (modelSelect) {
  modelSelect.addEventListener("change", function (e) {
    currentModel = e.target.value;
    if (currentModel === "qwen3.5:0.8b") {
      modelSelect.title = "轻量模型，响应速度快";
    } else if (currentModel === "qwen3.5:2b") {
      modelSelect.title = "中等模型，平衡速度与效果";
    } else if (currentModel === "qwen3.5:4b") {
      modelSelect.title = "大型模型，效果更好但速度较慢";
    } else {
      modelSelect.title = "";
    }
  });
}

function createSkeletonPlaceholder() {
  const wrap = document.createElement("div");
  wrap.className = "content-placeholder";
  const l1 = document.createElement("div");
  l1.className = "skeleton-line w-85";
  const l2 = document.createElement("div");
  l2.className = "skeleton-line w-92";
  const l3 = document.createElement("div");
  l3.className = "skeleton-line w-72";
  const l4 = document.createElement("div");
  l4.className = "skeleton-line w-64";
  wrap.appendChild(l1);
  wrap.appendChild(l2);
  wrap.appendChild(l3);
  wrap.appendChild(l4);
  return wrap;
}

function createContentErrorBlock(title, desc, onRetry) {
  const box = document.createElement("div");
  box.className = "content-error";
  const t = document.createElement("div");
  t.className = "content-error-title";
  t.textContent = title;
  const d = document.createElement("div");
  d.className = "content-error-desc";
  d.textContent = desc;
  const actions = document.createElement("div");
  actions.className = "content-error-actions";
  const retryBtn = document.createElement("button");
  retryBtn.className = "content-action-btn primary";
  retryBtn.type = "button";
  retryBtn.textContent = "重试";
  retryBtn.addEventListener("click", onRetry);
  actions.appendChild(retryBtn);
  box.appendChild(t);
  box.appendChild(d);
  box.appendChild(actions);
  return box;
}

async function runChatRequest(payload, options = {}) {
  const echoUser = options.echoUser !== false;
  autoScrollEnabled = true;

  if (activeAbortController) {
    try {
      activeAbortController.abort();
    } catch (e) {
    }
  }

  const abortController = new AbortController();
  activeAbortController = abortController;

  if (echoUser) {
    if (payload.image) {
      addMessage("user", payload.image, true);
    }
    if (payload.message) {
      addMessage("user", payload.message, false);
    }
  }

  sendBtn.disabled = true;
  hideTyping();

  let assistantMessage = "";
  let thinkingContent = "";
  let thinkingFinished = false;
  let thinkingEnabled = null;
  let assistantMessageDiv = null;
  let assistantMessageContentDiv = null;
  let assistantAnswerDiv = null;
  let contentPlaceholderDiv = null;
  let contentCollapseRow = null;
  let contentCollapseBtn = null;
  let thinkingBlockDiv = null;
  let thinkingBadgeDiv = null;
  let thinkingStageText = null;
  let thinkingProgressDiv = null;
  let thinkingBodyPre = null;
  let thinkingToggleBtn = null;
  let thinkingCancelBtn = null;
  let thinkingTimerId = null;
  let thinkingStepIndex = 0;
  const thinkingSteps = ["正在分析你的问题", "正在检索相关知识", "正在整理思路", "正在生成答案草稿", "正在润色表达"];

  function stopThinkingTimer() {
    if (thinkingTimerId) {
      clearInterval(thinkingTimerId);
      thinkingTimerId = null;
    }
  }

  function updateThinkingStep(forceIndex) {
    if (!thinkingStageText || !thinkingProgressDiv) return;
    const idx = typeof forceIndex === "number" ? forceIndex : thinkingStepIndex;
    thinkingStageText.textContent = thinkingSteps[Math.max(0, Math.min(idx, thinkingSteps.length - 1))];
    thinkingProgressDiv.textContent = `${Math.min(idx + 1, thinkingSteps.length)}/${thinkingSteps.length} 步`;
  }

  function startThinkingTimer() {
    stopThinkingTimer();
    thinkingStepIndex = 0;
    updateThinkingStep(0);
    thinkingTimerId = setInterval(() => {
      if (thinkingFinished) return;
      thinkingStepIndex = Math.min(thinkingStepIndex + 1, thinkingSteps.length - 1);
      updateThinkingStep(thinkingStepIndex);
    }, 1300);
  }

  function ensureAssistantMessage() {
    if (assistantMessageDiv) return;

    assistantMessageDiv = document.createElement("div");
    assistantMessageDiv.className = "message-wrapper ai-message";

    const avatar = document.createElement("div");
    avatar.className = "avatar ai";
    avatar.textContent = "AI";

    assistantMessageContentDiv = document.createElement("div");
    assistantMessageContentDiv.className = "message-content";

    assistantAnswerDiv = document.createElement("div");
    assistantAnswerDiv.className = "assistant-answer";

    contentCollapseRow = document.createElement("div");
    contentCollapseRow.className = "content-collapse-row";
    contentCollapseBtn = document.createElement("button");
    contentCollapseBtn.type = "button";
    contentCollapseBtn.className = "content-action-btn";
    contentCollapseBtn.textContent = "展开更多";
    contentCollapseBtn.addEventListener("click", () => {
      if (!assistantAnswerDiv) return;
      const collapsed = assistantAnswerDiv.classList.contains("content-collapsed");
      if (collapsed) {
        assistantAnswerDiv.classList.remove("content-collapsed");
        contentCollapseBtn.textContent = "收起";
      } else {
        assistantAnswerDiv.classList.add("content-collapsed");
        contentCollapseBtn.textContent = "展开更多";
      }
      scrollIfNeeded();
    });
    contentCollapseRow.appendChild(contentCollapseBtn);

    assistantMessageContentDiv.appendChild(assistantAnswerDiv);
    assistantMessageContentDiv.appendChild(contentCollapseRow);
    assistantMessageDiv.appendChild(avatar);
    assistantMessageDiv.appendChild(assistantMessageContentDiv);

    const typingEl = getTypingIndicatorElement();
    if (typingEl) {
      chatContainer.insertBefore(assistantMessageDiv, typingEl);
    } else {
      chatContainer.appendChild(assistantMessageDiv);
    }
    scrollIfNeeded();
  }

  function ensureContentPlaceholder() {
    ensureAssistantMessage();
    if (!assistantAnswerDiv) return;
    if (contentPlaceholderDiv) return;
    assistantAnswerDiv.innerHTML = "";
    contentPlaceholderDiv = createSkeletonPlaceholder();
    assistantAnswerDiv.appendChild(contentPlaceholderDiv);
  }

  function clearContentPlaceholder() {
    if (!contentPlaceholderDiv) return;
    try {
      contentPlaceholderDiv.remove();
    } catch (e) {
    }
    contentPlaceholderDiv = null;
  }

  function setContentError(desc) {
    ensureAssistantMessage();
    stopThinkingTimer();
    clearContentPlaceholder();
    if (assistantAnswerDiv) {
      assistantAnswerDiv.innerHTML = "";
      assistantAnswerDiv.appendChild(createContentErrorBlock("思考过程中出错了", desc || "请稍后重试", () => {
        if (!lastSendPayload) return;
        runChatRequest(lastSendPayload, { echoUser: false });
      }));
    }
  }

  function setContentCanceled() {
    ensureAssistantMessage();
    stopThinkingTimer();
    clearContentPlaceholder();
    if (assistantAnswerDiv) {
      assistantAnswerDiv.innerHTML = "";
      assistantAnswerDiv.appendChild(createContentErrorBlock("已取消", "你已中断本次思考，可点击重试继续生成。", () => {
        if (!lastSendPayload) return;
        runChatRequest(lastSendPayload, { echoUser: false });
      }));
    }
  }

  function ensureThinkingBlock() {
    ensureAssistantMessage();
    if (thinkingBlockDiv) return;

    thinkingBlockDiv = document.createElement("div");
    thinkingBlockDiv.className = "thinking-block";

    const top = document.createElement("div");
    top.className = "thinking-top";

    const title = document.createElement("div");
    title.className = "thinking-title";
    const badge = document.createElement("span");
    badge.className = "thinking-badge";
    badge.textContent = "思考中";
    thinkingBadgeDiv = badge;
    const titleText = document.createElement("span");
    titleText.textContent = "深度思考";
    title.appendChild(badge);
    title.appendChild(titleText);

    const actions = document.createElement("div");
    actions.className = "thinking-actions";

    thinkingCancelBtn = document.createElement("button");
    thinkingCancelBtn.type = "button";
    thinkingCancelBtn.className = "thinking-btn danger";
    thinkingCancelBtn.textContent = "取消";
    thinkingCancelBtn.addEventListener("click", () => {
      try {
        abortController.abort();
      } catch (e) {
      }
    });

    thinkingToggleBtn = document.createElement("button");
    thinkingToggleBtn.type = "button";
    thinkingToggleBtn.className = "thinking-icon-btn";
    thinkingToggleBtn.innerHTML = "<svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><polyline points=\"6 9 12 15 18 9\"></polyline></svg>";
    thinkingToggleBtn.addEventListener("click", () => {
      if (!thinkingBlockDiv) return;
      thinkingBlockDiv.classList.toggle("expanded");
      const expanded = thinkingBlockDiv.classList.contains("expanded");
      thinkingToggleBtn.innerHTML = expanded
        ? "<svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><polyline points=\"18 15 12 9 6 15\"></polyline></svg>"
        : "<svg width=\"16\" height=\"16\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\"><polyline points=\"6 9 12 15 18 9\"></polyline></svg>";
      scrollIfNeeded();
    });

    actions.appendChild(thinkingCancelBtn);
    actions.appendChild(thinkingToggleBtn);

    top.appendChild(title);
    top.appendChild(actions);

    const meta = document.createElement("div");
    meta.className = "thinking-meta";

    const stage = document.createElement("div");
    stage.className = "thinking-stage";
    const dots = document.createElement("span");
    dots.className = "thinking-dots";
    dots.innerHTML = "<span></span><span></span><span></span>";
    thinkingStageText = document.createElement("span");
    thinkingStageText.textContent = thinkingSteps[0];
    stage.appendChild(dots);
    stage.appendChild(thinkingStageText);

    thinkingProgressDiv = document.createElement("div");
    thinkingProgressDiv.className = "thinking-progress";
    thinkingProgressDiv.textContent = `1/${thinkingSteps.length} 步`;

    meta.appendChild(stage);
    meta.appendChild(thinkingProgressDiv);

    const body = document.createElement("div");
    body.className = "thinking-body";
    const pre = document.createElement("pre");
    pre.textContent = "";
    thinkingBodyPre = pre;
    body.appendChild(pre);

    thinkingBlockDiv.appendChild(top);
    thinkingBlockDiv.appendChild(meta);
    thinkingBlockDiv.appendChild(body);

    assistantMessageContentDiv.insertBefore(thinkingBlockDiv, assistantAnswerDiv);

    startThinkingTimer();
  }

  function setThinkingState(state) {
    if (!thinkingBadgeDiv) return;
    if (thinkingBlockDiv) {
      thinkingBlockDiv.classList.toggle("is-stopped", state !== "running");
    }
    if (state === "running") {
      thinkingBadgeDiv.textContent = "思考中";
      if (thinkingCancelBtn) thinkingCancelBtn.style.display = "inline-flex";
    } else if (state === "done") {
      thinkingBadgeDiv.textContent = "已完成";
      if (thinkingCancelBtn) thinkingCancelBtn.style.display = "none";
    } else if (state === "error") {
      thinkingBadgeDiv.textContent = "出错";
      if (thinkingCancelBtn) thinkingCancelBtn.style.display = "none";
    } else if (state === "canceled") {
      thinkingBadgeDiv.textContent = "已取消";
      if (thinkingCancelBtn) thinkingCancelBtn.style.display = "none";
    }
  }

  function finalizeThinkingIfNeeded() {
    if (!thinkingBlockDiv || thinkingFinished) return;
    thinkingFinished = true;
    stopThinkingTimer();
    setThinkingState("done");
    if (thinkingStageText) thinkingStageText.textContent = "思考完成，正在输出答案";
    if (thinkingProgressDiv) thinkingProgressDiv.textContent = `${thinkingSteps.length}/${thinkingSteps.length} 步`;
  }

  function applyContentCollapseIfNeeded() {
    if (!assistantAnswerDiv || !contentCollapseRow) return;
    const height = assistantAnswerDiv.scrollHeight;
    if (height > 720) {
      assistantAnswerDiv.classList.add("content-collapsed");
      contentCollapseRow.classList.add("visible");
      contentCollapseBtn.textContent = "展开更多";
    } else {
      assistantAnswerDiv.classList.remove("content-collapsed");
      contentCollapseRow.classList.remove("visible");
    }
  }

  ensureAssistantMessage();
  ensureContentPlaceholder();

  try {
    const response = await fetchWithAuth("/api/unified/chat/stream", {
      method: "POST",
      signal: abortController.signal,
      body: JSON.stringify({
        sessionId: payload.sessionId,
        message: payload.message,
        model: payload.model,
        image: payload.image
      }),
    });

    if (!response.ok) {
      let errMsg = "发送失败";
      const ct = (response.headers.get("content-type") || "").toLowerCase();
      if (ct.includes("application/json")) {
        const errorData = await response.json().catch(() => ({}));
        errMsg = errorData.message || errMsg;
      } else {
        const text = await response.text().catch(() => "");
        if (response.status === 413) {
          errMsg = "图片过大或被服务器拒绝（413 Request Entity Too Large）";
        } else {
          errMsg = text || errMsg;
        }
      }
      throw new Error(errMsg);
    }

    const reader = response.body.getReader();
    const decoder = new TextDecoder();
    let streamStarted = false;
    let doneSeen = false;
    let errorSeen = false;
    let renderScheduled = false;
    let lastRenderTime = 0;
    let pendingLine = "";

    function renderAssistantMarkdown(mode = "normal") {
      if (!assistantAnswerDiv) return;
      const now = Date.now();
      const minInterval = mode === "boundary" ? 140 : 240;
      const force = mode === "force";
      if (!force && now - lastRenderTime < minInterval) {
        if (!renderScheduled) {
          renderScheduled = true;
          setTimeout(() => {
            renderScheduled = false;
            renderAssistantMarkdown("force");
          }, minInterval - (now - lastRenderTime));
        }
        return;
      }
      lastRenderTime = now;
      clearContentPlaceholder();
      try {
        assistantAnswerDiv.innerHTML = marked.parse(assistantMessage);
      } catch (e) {
        setThinkingState("error");
        setContentError("内容渲染失败，请点击重试重新生成。");
      }
    }

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      pendingLine += decoder.decode(value, { stream: true });
      const lines = pendingLine.split("\n");
      pendingLine = lines.pop() || "";

      for (const line of lines) {
        if (!line.trim()) continue;

        try {
          const data = JSON.parse(line);

          if (data && data.type === "meta") {
            const enable = typeof data.thinking === "boolean" ? data.thinking : null;
            if (enable !== null) {
              thinkingEnabled = enable;
              if (enable) {
                ensureThinkingBlock();
                setThinkingState("running");
              } else {
                stopThinkingTimer();
                if (thinkingBlockDiv) {
                  try {
                    thinkingBlockDiv.remove();
                  } catch (e) {
                  }
                  thinkingBlockDiv = null;
                  thinkingBadgeDiv = null;
                  thinkingStageText = null;
                  thinkingProgressDiv = null;
                  thinkingBodyPre = null;
                  thinkingToggleBtn = null;
                  thinkingCancelBtn = null;
                }
              }
            }
            continue;
          }

          const chunkThinking =
            (data.message && data.message.thinking) ? data.message.thinking :
            (data.thinking ? data.thinking : "");
          const chunkContent =
            (data.type === "message" && data.content) ? data.content :
            (data.message && typeof data.message.content === "string") ? data.message.content :
            (typeof data.content === "string") ? data.content :
            (typeof data.response === "string") ? data.response :
            "";

          if (data.type === "error" || data.error) {
            setThinkingState("error");
            setContentError(data.message || data.error);
            errorSeen = true;
            sendBtn.disabled = false;
            continue;
          }

          if (data.type === "done" || data.done === true) {
            doneSeen = true;
            finalizeThinkingIfNeeded();
            if (assistantMessageDiv) {
              if (assistantMessage) {
                renderAssistantMarkdown("force");
                applyContentCollapseIfNeeded();
              }
            } else if (assistantMessage) {
              addMessage("assistant", assistantMessage);
            }
            sendBtn.disabled = false;
            loadConversationList();
            setTimeout(() => loadConversationList(), 1200);
            setTimeout(() => loadConversationList(), 2600);
            setTimeout(() => loadConversationList(), 5200);
            continue;
          }

          if (!streamStarted && (chunkThinking || chunkContent)) {
            streamStarted = true;
          }

          if (chunkThinking) {
            if (thinkingEnabled !== false) {
              thinkingEnabled = true;
              ensureThinkingBlock();
            }
            if (!thinkingFinished) {
              thinkingContent += chunkThinking;
              if (thinkingBodyPre) thinkingBodyPre.textContent = thinkingContent;
              scrollIfNeeded();
            }
          }

          if (chunkContent) {
            ensureAssistantMessage();
            finalizeThinkingIfNeeded();
            assistantMessage += chunkContent;
            const boundary =
              chunkContent.includes("\n") ||
              chunkContent.includes("```") ||
              /[。！？.!?]/.test(chunkContent) ||
              /^#{1,6}\s/.test(chunkContent) ||
              /^(\s*[-*+]\s|\s*\d+\.\s)/.test(chunkContent);
            renderAssistantMarkdown(boundary ? "boundary" : "normal");
            scrollIfNeeded();
          }
        } catch (e) {
          console.error("Parse error:", e, "Line:", line);
        }
      }
    }

    if (pendingLine.trim()) {
      try {
        const data = JSON.parse(pendingLine);
        if (data.type === "error" || data.error) {
          hideTyping();
          showError(data.message || data.error);
          errorSeen = true;
        }
      } catch {}
    }

    if (doneSeen && !streamStarted && !assistantMessage && !errorSeen) {
      setThinkingState("error");
      setContentError("未找到相关内容，可调整提问方式或切换模型。");
    }
  } catch (error) {
    const name = (error && error.name) ? error.name : "";
    if (name === "AbortError") {
      setThinkingState("canceled");
      setContentCanceled();
    } else {
      setThinkingState("error");
      setContentError(error.message || "发送消息失败");
    }
    sendBtn.disabled = false;
  } finally {
    stopThinkingTimer();
    currentImageBase64 = null;
    if (imageInput) {
      imageInput.value = "";
    }
    if (imagePreview) {
      imagePreview.src = "";
      imagePreview.style.display = "none";
    }
    if (removeImageBtn) {
      removeImageBtn.style.display = "none";
    }
    if (imageLabel) {
      imageLabel.style.display = "block";
    }
    if (activeAbortController === abortController) {
      activeAbortController = null;
    }
  }
}

async function sendMessage() {
  const message = messageInput.value.trim();
  if (!message && !currentImageBase64) {
    showError("请输入消息或选择图片");
    return;
  }

  if (!sessionId) {
    try {
      await createNewConversation(true);
    } catch (e) {
      sessionId = generateSessionId();
    }
  }

  const payload = {
    sessionId,
    message,
    model: currentModel,
    image: currentImageBase64
  };
  lastSendPayload = payload;

  messageInput.value = "";
  await runChatRequest(payload, { echoUser: true });
}

sendBtn.addEventListener("click", sendMessage);

messageInput.addEventListener("keypress", function (e) {
  if (e.key === "Enter" && !e.shiftKey) {
    e.preventDefault();
    sendMessage();
  }
});

resetBtn.addEventListener("click", async function () {
  if (confirm("确定要重置对话吗？")) {
    try {
      await createNewConversation(false);
      showSuccess("已开启新对话");
    } catch (e) {
      sessionId = null;
      clearChatToWelcome();
      showSuccess("对话已重置");
    }
  }
});

if (logoutBtn) {
  logoutBtn.addEventListener("click", function() {
    localStorage.removeItem('token');
    localStorage.removeItem('userId');
    localStorage.removeItem('username');
    gotoAppHash("/login");
  });
}

function showSuccess(message) {
  const successDiv = document.createElement("div");
  successDiv.style.cssText = `
    position: fixed;
    top: 20px;
    right: 20px;
    background: #4caf50;
    color: white;
    padding: 12px 24px;
    border-radius: 8px;
    box-shadow: 0 4px 12px rgba(0,0,0,0.2);
    z-index: 1000;
    animation: slideIn 0.3s ease-out;
  `;
  successDiv.textContent = message;
  document.body.appendChild(successDiv);
  
  setTimeout(() => {
    successDiv.style.opacity = '0';
    setTimeout(() => successDiv.remove(), 300);
  }, 2000);
}

const style = document.createElement('style');
style.textContent = `
  @keyframes slideIn {
    from {
      transform: translateX(100%);
      opacity: 0;
    }
    to {
      transform: translateX(0);
      opacity: 1;
    }
  }
`;
document.head.appendChild(style);

// 移动端侧边栏切换
const sidebarToggle = document.getElementById('sidebarToggle');
const sidebarOverlay = document.getElementById('sidebarOverlay');
const sidebar = document.querySelector('.sidebar');

if (sidebarToggle && sidebar && sidebarOverlay) {
  sidebarToggle.addEventListener('click', function() {
    sidebar.classList.toggle('open');
    sidebarOverlay.classList.toggle('open');
  });

  sidebarOverlay.addEventListener('click', function() {
    sidebar.classList.remove('open');
    sidebarOverlay.classList.remove('open');
  });

  // 点击对话列表项后关闭侧边栏（移动端）
  conversationList.addEventListener('click', function(e) {
    if (e.target.closest('.conversation-item') && window.innerWidth <= 768) {
      sidebar.classList.remove('open');
      sidebarOverlay.classList.remove('open');
    }
  });
}

function syncComposerHeight() {
  const inputContainer = document.querySelector(".input-area");
  if (!inputContainer) return;
  const h = Math.ceil(inputContainer.getBoundingClientRect().height);
  document.documentElement.style.setProperty("--composer-h", h + "px");
}

window.addEventListener("resize", syncComposerHeight);
setTimeout(syncComposerHeight, 0);

function scrollChatToBottom() {
  if (!chatContainer) return;
  scrollIfNeeded(true);
}

setTimeout(scrollChatToBottom, 0);
