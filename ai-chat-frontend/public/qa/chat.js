function isLocalHost(hostname) {
  return hostname === "localhost" || hostname === "127.0.0.1" || hostname === "::1";
}

const API_BASE = (window.API_BASE || (isLocalHost(window.location.hostname) ? "http://localhost:8081" : "")).replace(/\/$/, "");

let sessionId = null;
let currentImageBase64 = null;
let currentModel = "glm-4.6v-Flash";
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
let conversationMetaBySessionId = new Map();

let autoScrollEnabled = true;

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

if (backBtn) {
  backBtn.addEventListener("click", function () {
    gotoAppHash("/");
  });
}

if (modelSelect) {
  modelSelect.value = currentModel;
}

userInfo.textContent = `欢迎, ${username}`;

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
    <div class="message assistant">
      <div class="avatar">AI</div>
      <div class="message-content">
        你好！我是AI助手。我可以与你进行文字对话，也可以分析图片。有什么我可以帮助你的吗？
      </div>
    </div>
    <div class="typing-indicator" id="typingIndicator">
      <span></span>
      <span></span>
      <span></span>
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
    typing.innerHTML = "<span></span><span></span><span></span>";
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
  messageDiv.className = `message ${role}`;

  const avatar = document.createElement("div");
  avatar.className = "avatar";
  avatar.textContent = role === "user" ? "U" : "AI";

  const messageContent = document.createElement("div");
  messageContent.className = `message-content ${isThinking ? "thinking" : ""}`;

  if (isImage) {
    const img = document.createElement("img");
    img.src = content;
    img.style.maxWidth = "100%";
    img.style.borderRadius = "10px";
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
      imagePreview.src = currentImageBase64;
      imagePreview.style.display = "block";
      removeImageBtn.style.display = "block";
      imageLabel.style.display = "none";
      syncComposerHeight();
      scrollChatToBottom();
    };
    reader.readAsDataURL(file);
  }
});

removeImageBtn.addEventListener("click", function () {
  currentImageBase64 = null;
  imageInput.value = "";
  imagePreview.src = "";
  imagePreview.style.display = "none";
  removeImageBtn.style.display = "none";
  imageLabel.style.display = "block";
  syncComposerHeight();
});

modelSelect.addEventListener("change", function (e) {
  currentModel = e.target.value;
});

async function sendMessage() {
  const message = messageInput.value.trim();
  if (!message && !currentImageBase64) {
    showError("请输入消息或选择图片");
    return;
  }
  autoScrollEnabled = true;

  if (!sessionId) {
    try {
      await createNewConversation(true);
    } catch (e) {
      sessionId = generateSessionId();
    }
  }

  const hasImage = currentImageBase64 !== null;
  if (hasImage) {
    addMessage("user", currentImageBase64, true);
  }
  if (message) {
    addMessage("user", message, false);
  }
  messageInput.value = "";
  sendBtn.disabled = true;
  showTyping();

  try {
    const response = await fetchWithAuth("/api/unified/chat/stream", {
      method: "POST",
      body: JSON.stringify({
        sessionId: sessionId,
        message: message,
        model: currentModel,
        image: currentImageBase64
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
    let assistantMessage = "";
    let thinkingContent = "";
    let isThinking = false;
    let thinkingFinished = false;
    let assistantMessageDiv = null;
    let assistantMessageContentDiv = null;
    let assistantAnswerDiv = null;
    let thinkingBlockDiv = null;
    let thinkingContainerDiv = null;
    let thinkingStatusDiv = null;
    let foldButton = null;
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
      assistantAnswerDiv.innerHTML = marked.parse(assistantMessage);
    }

    function ensureAssistantMessage() {
      if (assistantMessageDiv) return;

      assistantMessageDiv = document.createElement("div");
      assistantMessageDiv.className = "message assistant";

      const avatar = document.createElement("div");
      avatar.className = "avatar";
      avatar.textContent = "AI";

      assistantMessageContentDiv = document.createElement("div");
      assistantMessageContentDiv.className = "message-content";

      assistantAnswerDiv = document.createElement("div");
      assistantMessageContentDiv.appendChild(assistantAnswerDiv);

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

    function ensureThinkingBlock() {
      ensureAssistantMessage();
      if (thinkingBlockDiv) return;

      thinkingBlockDiv = document.createElement("div");
      thinkingBlockDiv.className = "thinking-block";

      thinkingStatusDiv = document.createElement("div");
      thinkingStatusDiv.className = "thinking-status";
      thinkingStatusDiv.textContent = "思考中...";

      foldButton = document.createElement("button");
      foldButton.className = "fold-button";
      foldButton.innerHTML = "<svg width=16 height=16><path d='M4 8L8 12L12 8' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'/></svg>";

      thinkingContainerDiv = document.createElement("div");
      thinkingContainerDiv.className = "thinking-container";

      function toggleThinking() {
        if (!thinkingContainerDiv) return;
        const collapsed = thinkingContainerDiv.style.display === "none";
        thinkingContainerDiv.style.display = collapsed ? "block" : "none";
        foldButton.innerHTML = collapsed
          ? "<svg width=16 height=16><path d='M4 8L8 12L12 8' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'/></svg>"
          : "<svg width=16 height=16><path d='M8 4L4 8L8 12' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'/></svg>";
        if (thinkingFinished) {
          thinkingStatusDiv.textContent = collapsed ? "思考完毕（点击展开）" : "思考完毕（点击折叠）";
        }
      }

      foldButton.addEventListener("click", toggleThinking);
      thinkingStatusDiv.addEventListener("click", toggleThinking);

      thinkingBlockDiv.appendChild(foldButton);
      thinkingBlockDiv.appendChild(thinkingStatusDiv);
      thinkingBlockDiv.appendChild(thinkingContainerDiv);

      assistantMessageContentDiv.insertBefore(thinkingBlockDiv, assistantAnswerDiv);
    }

    function finalizeThinkingIfNeeded() {
      if (!thinkingBlockDiv || thinkingFinished) return;
      thinkingFinished = true;
      thinkingContainerDiv.style.display = "none";
      foldButton.innerHTML = "<svg width=16 height=16><path d='M8 4L4 8L8 12' stroke='currentColor' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'/></svg>";
      thinkingStatusDiv.textContent = "思考完毕（点击展开）";
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
            hideTyping();
            showError(data.message || data.error);
            errorSeen = true;
            sendBtn.disabled = false;
            continue;
          }

          if (data.type === "done" || data.done === true) {
            doneSeen = true;
            hideTyping();
            finalizeThinkingIfNeeded();
            if (assistantMessageDiv) {
              if (assistantMessage) {
                renderAssistantMarkdown("force");
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
            hideTyping();
          }

          if (chunkThinking) {
            ensureThinkingBlock();
            if (!isThinking && !thinkingFinished) {
              isThinking = true;
              thinkingContent = "";
            }
            if (!thinkingFinished) {
              thinkingContent += chunkThinking;
              thinkingContainerDiv.textContent = thinkingContent;
              scrollIfNeeded();
            }
          }

          if (chunkContent) {
            ensureAssistantMessage();
            if (isThinking) {
              finalizeThinkingIfNeeded();
              isThinking = false;
            }
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
      showError("模型未返回任何内容，请稍后重试或切换模型");
    }
  } catch (error) {
    hideTyping();
    showError(error.message || "发送消息失败");
    sendBtn.disabled = false;
  } finally {
    currentImageBase64 = null;
    imageInput.value = "";
    imagePreview.src = "";
    imagePreview.style.display = "none";
    removeImageBtn.style.display = "none";
    imageLabel.style.display = "block";
  }
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
  const inputContainer = document.querySelector(".input-container");
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
