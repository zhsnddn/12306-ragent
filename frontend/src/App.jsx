import { useEffect, useRef, useState, startTransition } from "react";

const STORAGE_KEY = "agent12306.session";
const SAMPLE_PROMPTS = [
  {
    title: "票务查询",
    message: "帮我查一下明天北京到上海的高铁车票，优先二等座",
    note: "测试 MCP 查询和流式回复"
  },
  {
    title: "经停站测试",
    message: "帮我查询2026-03-10从杭州东到北京南的G814次列车经停站信息",
    note: "测试经停站和参数提取"
  },
  {
    title: "规则问答",
    message: "候补购票什么时候兑现，开车前还能改签吗",
    note: "测试 RAG 检索增强"
  }
];

const DEBUG_EVENT_TYPES = new Set(["reasoning", "tool_result", "summary", "prompt", "error"]);

export default function App() {
  const [sessionId, setSessionId] = useState(() => window.localStorage.getItem(STORAGE_KEY) || "");
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("候补购票什么时候兑现，开车前还能改签吗");
  const [streaming, setStreaming] = useState(false);
  const [health, setHealth] = useState("检查中");
  const [errorText, setErrorText] = useState("");
  const [mode, setMode] = useState("stream");
  const [eventLog, setEventLog] = useState([]);
  const [documents, setDocuments] = useState([]);
  const [documentError, setDocumentError] = useState("");
  const [loadingDocuments, setLoadingDocuments] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [uploadStageText, setUploadStageText] = useState("");
  const [title, setTitle] = useState("");
  const [category, setCategory] = useState("rule");
  const [selectedFile, setSelectedFile] = useState(null);
  const [knowledgeQuery, setKnowledgeQuery] = useState("候补什么时候兑现");
  const [knowledgeResults, setKnowledgeResults] = useState([]);
  const [searchingKnowledge, setSearchingKnowledge] = useState(false);
  const abortRef = useRef(null);
  const uploadPollRef = useRef(null);
  const scrollRef = useRef(null);
  const fileInputRef = useRef(null);

  useEffect(() => {
    fetch("/api/assistant")
      .then((response) => response.text())
      .then(() => setHealth("服务已连接"))
      .catch(() => setHealth("服务未连接"));
  }, []);

  useEffect(() => {
    loadDocuments();
  }, []);

  useEffect(() => {
    if (sessionId) {
      window.localStorage.setItem(STORAGE_KEY, sessionId);
    } else {
      window.localStorage.removeItem(STORAGE_KEY);
    }
  }, [sessionId]);

  useEffect(() => {
    const container = scrollRef.current;
    if (container) {
      container.scrollTop = container.scrollHeight;
    }
  }, [messages, streaming]);

  useEffect(() => () => {
    abortRef.current?.abort();
    stopDocumentPolling();
  }, []);

  async function loadDocuments(silent = false) {
    if (!silent) {
      setLoadingDocuments(true);
      setDocumentError("");
    }
    try {
      const response = await fetch("/api/knowledge/documents");
      if (!response.ok) {
        throw new Error(`文档列表获取失败: ${response.status}`);
      }
      const payload = await response.json();
      const nextDocuments = Array.isArray(payload) ? payload : [];
      setDocuments(nextDocuments);
      const currentActive = nextDocuments.find((item) => item.parseStatus !== "READY" && item.parseStatus !== "FAILED");
      if (currentActive?.progressMessage) {
        setUploadStageText(currentActive.progressMessage);
      }
    } catch (error) {
      if (!silent) {
        setDocumentError(error.message || "文档列表获取失败");
      }
    } finally {
      if (!silent) {
        setLoadingDocuments(false);
      }
    }
  }

  async function handleUpload(event) {
    event.preventDefault();
    if (!selectedFile || uploading) {
      return;
    }

    const formData = new FormData();
    formData.append("file", selectedFile);
    if (title.trim()) {
      formData.append("title", title.trim());
    }
    if (category.trim()) {
      formData.append("category", category.trim());
    }

    setUploading(true);
    setUploadProgress(0);
    setUploadStageText("准备上传");
    setDocumentError("");
    startDocumentPolling();
    try {
      const payload = await uploadDocument(formData, (progress) => {
        setUploadProgress(progress);
        setUploadStageText(progress < 100 ? `文件上传中 ${progress}%` : "文件已传输，等待后端处理");
      });

      setTitle("");
      setCategory("rule");
      setSelectedFile(null);
      if (fileInputRef.current) {
        fileInputRef.current.value = "";
      }
      await loadDocuments();
      setKnowledgeQuery(payload.title || knowledgeQuery);
    } catch (error) {
      setDocumentError(error.message || "文档上传失败");
    } finally {
      setUploading(false);
      setUploadProgress(0);
      setUploadStageText("");
      stopDocumentPolling();
    }
  }

  async function handleKnowledgeSearch(event) {
    event.preventDefault();
    const query = knowledgeQuery.trim();
    if (!query || searchingKnowledge) {
      return;
    }

    setSearchingKnowledge(true);
    setDocumentError("");
    try {
      const response = await fetch(`/api/knowledge/search?q=${encodeURIComponent(query)}`);
      if (!response.ok) {
        throw new Error(`知识检索失败: ${response.status}`);
      }
      const payload = await response.json();
      setKnowledgeResults(Array.isArray(payload) ? payload : []);
    } catch (error) {
      setDocumentError(error.message || "知识检索失败");
      setKnowledgeResults([]);
    } finally {
      setSearchingKnowledge(false);
    }
  }

  async function handleSubmit(event) {
    event.preventDefault();
    const message = input.trim();
    if (!message || streaming) {
      return;
    }

    if (mode === "sync") {
      await submitSync(message);
      return;
    }
    await submitStream(message);
  }

  async function submitSync(message) {
    const requestSessionId = sessionId || null;
    const userMessageId = crypto.randomUUID();
    const assistantMessageId = crypto.randomUUID();

    setStreaming(true);
    setErrorText("");
    pushUserAndAssistantShell(userMessageId, assistantMessageId, message, "等待同步结果");
    setInput("");

    try {
      const response = await fetch("/api/assistant/chat", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          sessionId: requestSessionId,
          message
        })
      });

      const payload = await response.json();
      if (!response.ok || payload.success === false) {
        throw new Error(payload.answer || `请求失败: ${response.status}`);
      }

      if (payload.sessionId) {
        setSessionId(payload.sessionId);
      }

      setEventLog((previous) => [
        makeEvent("sync_response", payload.answer || ""),
        ...previous
      ].slice(0, 24));

      setMessages((previous) => previous.map((item) => item.id === assistantMessageId
        ? {
            ...item,
            text: payload.answer || "",
            timestamp: formatTime(new Date()),
            events: [makeEvent("sync_response", payload.answer || "")]
          }
        : item));
    } catch (error) {
      const messageText = error.message || "请求失败，请稍后重试";
      setErrorText(messageText);
      setMessages((previous) => previous.map((item) => item.id === assistantMessageId
        ? { ...item, text: messageText, timestamp: formatTime(new Date()) }
        : item));
    } finally {
      setStreaming(false);
    }
  }

  async function submitStream(message) {
    const requestSessionId = sessionId || null;
    const userMessageId = crypto.randomUUID();
    const assistantMessageId = crypto.randomUUID();

    setStreaming(true);
    setErrorText("");
    pushUserAndAssistantShell(userMessageId, assistantMessageId, message, "流式生成中");
    setInput("");

    const controller = new AbortController();
    abortRef.current = controller;

    try {
      if (!requestSessionId) {
        const createdSessionId = await fetchSessionId(message);
        if (createdSessionId) {
          setSessionId(createdSessionId);
        }
      }

      const response = await fetch("/api/assistant/chat/stream", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Accept: "text/event-stream"
        },
        body: JSON.stringify({
          sessionId: requestSessionId,
          message
        }),
        signal: controller.signal
      });

      if (!response.ok || !response.body) {
        throw new Error(`请求失败: ${response.status}`);
      }

      await consumeSseStream(response.body, (eventName, payload) => {
        startTransition(() => {
          const eventEntry = makeEvent(eventName, payload?.text || "");
          if (DEBUG_EVENT_TYPES.has(eventName)) {
            setEventLog((previous) => [eventEntry, ...previous].slice(0, 24));
          }

          setMessages((previous) => previous.map((item) => {
            if (item.id !== assistantMessageId) {
              return item;
            }

            const nextEvents = [...(item.events || []), eventEntry].slice(-10);

            if (eventName === "agent_result") {
              return {
                ...item,
                text: payload?.text || item.text,
                timestamp: payload?.last ? formatTime(new Date()) : "流式生成中",
                events: nextEvents
              };
            }

            if (eventName === "error") {
              return {
                ...item,
                text: payload?.text || "处理失败，请稍后重试",
                timestamp: formatTime(new Date()),
                events: nextEvents
              };
            }

            return {
              ...item,
              events: nextEvents
            };
          }));
        });
      });
    } catch (error) {
      if (error.name !== "AbortError") {
        const messageText = error.message || "请求失败，请稍后重试";
        setErrorText(messageText);
        setMessages((previous) => previous.map((item) => item.id === assistantMessageId
          ? { ...item, text: messageText, timestamp: formatTime(new Date()) }
          : item));
      }
    } finally {
      setStreaming(false);
      abortRef.current = null;
    }
  }

  function pushUserAndAssistantShell(userMessageId, assistantMessageId, message, assistantTimestamp) {
    setMessages((previous) => [
      ...previous,
      {
        id: userMessageId,
        role: "user",
        text: message,
        timestamp: formatTime(new Date())
      },
      {
        id: assistantMessageId,
        role: "assistant",
        text: "",
        timestamp: assistantTimestamp,
        events: []
      }
    ]);
  }

  function resetConversation() {
    abortRef.current?.abort();
    setStreaming(false);
    setMessages([]);
    setErrorText("");
    setSessionId("");
    setEventLog([]);
  }

  function stopStreaming() {
    abortRef.current?.abort();
    setStreaming(false);
  }

  async function handleDeleteDocument(documentId) {
    if (!window.confirm("确认删除这份文档吗？")) {
      return;
    }
    setDocumentError("");
    try {
      const response = await fetch(`/api/knowledge/documents/${documentId}`, {
        method: "DELETE"
      });
      if (!response.ok) {
        throw new Error(`删除失败: ${response.status}`);
      }
      setKnowledgeResults((previous) => previous.filter((item) => item.documentId !== documentId));
      await loadDocuments();
    } catch (error) {
      setDocumentError(error.message || "删除文档失败");
    }
  }

  function startDocumentPolling() {
    stopDocumentPolling();
    uploadPollRef.current = window.setInterval(() => {
      loadDocuments(true);
    }, 1200);
  }

  function stopDocumentPolling() {
    if (uploadPollRef.current) {
      window.clearInterval(uploadPollRef.current);
      uploadPollRef.current = null;
    }
  }

  const messageCount = messages.filter((item) => item.role === "user").length;
  const activeDocument = documents.find((item) => item.parseStatus !== "READY" && item.parseStatus !== "FAILED") || null;

  return (
    <div className="app-shell">
      <aside className="sidebar panel">
        <div className="brand">
          <span className="brand-eyebrow">React Client</span>
          <div className="brand-title">12306 Assistant</div>
          <p className="brand-copy">
            现在前端同时支持多轮聊天调试和 RAG 知识库管理，可直接在页面完成文档上传、检索测试和规则问答联调。
          </p>
        </div>

        <section className="status-card">
          <div className="status-line">
            <span className="status-label">后端状态</span>
            <span className="status-pill">{health}</span>
          </div>
          <div className="status-line">
            <span className="status-label">当前会话</span>
            <span className="status-pill">{sessionId ? "已建立" : "未建立"}</span>
          </div>
          <div className="status-line">
            <span className="status-label">模式</span>
            <span className="status-pill">{mode === "stream" ? "SSE" : "同步"}</span>
          </div>
          <div className="status-line">
            <span className="status-label">轮次</span>
            <span className="status-pill">{messageCount}</span>
          </div>
          <div className="session-id">{sessionId || "首次提问后自动生成 sessionId"}</div>
        </section>

        <section className="sample-card">
          <div className="status-line">
            <span className="status-label">调用模式</span>
            <span className="status-pill">{mode === "stream" ? "推荐" : "调试"}</span>
          </div>
          <div className="mode-switch">
            <button
              className={`mode-button ${mode === "stream" ? "active" : ""}`}
              type="button"
              onClick={() => setMode("stream")}
            >
              流式回复
            </button>
            <button
              className={`mode-button ${mode === "sync" ? "active" : ""}`}
              type="button"
              onClick={() => setMode("sync")}
            >
              同步回复
            </button>
          </div>
        </section>

        <div className="sidebar-actions">
          <button className="ghost-button" type="button" onClick={resetConversation}>
            清空会话
          </button>
          <button className="solid-button" type="button" onClick={stopStreaming} disabled={!streaming}>
            停止生成
          </button>
        </div>

        <section className="sample-card">
          <div className="status-line">
            <span className="status-label">快速测试</span>
            <span className="status-pill">{SAMPLE_PROMPTS.length} 条样例</span>
          </div>
          <div className="sample-list">
            {SAMPLE_PROMPTS.map((item) => (
              <button
                key={item.title}
                className="sample-button"
                type="button"
                onClick={() => setInput(item.message)}
              >
                <strong>{item.title}</strong>
                <span>{item.note}</span>
              </button>
            ))}
          </div>
        </section>

        <section className="sample-card event-panel">
          <div className="status-line">
            <span className="status-label">全局事件流</span>
            <span className="status-pill">{eventLog.length}</span>
          </div>
          <div className="event-list">
            {eventLog.length === 0 ? (
              <div className="empty-event">还没有调试事件</div>
            ) : eventLog.map((item) => (
              <div key={item.id} className={`event-item event-${item.type}`}>
                <div className="event-type">{item.type}</div>
                <div className="event-text">{item.text || "无文本载荷"}</div>
              </div>
            ))}
          </div>
        </section>
      </aside>

      <div className="main-stack">
        <main className="chat-panel panel">
          <header className="chat-header">
            <div>
              <h1 className="chat-title">多轮问答调试台</h1>
              <p className="chat-subtitle">
                前端走独立 Vite 开发服务器，聊天请求和知识库接口统一代理到 Spring Boot。
              </p>
            </div>
            <div className="chat-status">{streaming ? "模型正在生成" : "等待输入"}</div>
          </header>

          <section className="messages" ref={scrollRef}>
            {messages.length === 0 ? (
              <div className="welcome-card">
                <h2>先导入规则文档，再问规则问题</h2>
                <p>
                  上传 12306 规则文档后，可以直接问“候补什么时候兑现”“退票规则是什么”。RAG 会先检索，再把知识片段拼进模型上下文。
                </p>
              </div>
            ) : null}

            {messages.map((message) => (
              <article key={message.id} className={`message ${message.role}`}>
                <div className="avatar">{message.role === "user" ? "YOU" : "AI"}</div>
                <div className="bubble-wrap">
                  <div className="bubble">
                    <MessageText message={message} streaming={streaming} />
                  </div>
                  <div className="meta-row">
                    <span>{message.role === "user" ? "用户" : "助手"}</span>
                    <span>{message.timestamp}</span>
                  </div>
                  {message.events?.length ? (
                    <details className="debug-card">
                      <summary>查看本轮调试事件</summary>
                      <div className="debug-log">
                        {message.events.map((eventItem, index) => (
                          <div key={`${message.id}-${index}`} className="debug-log-item">
                            <strong>{eventItem.type}</strong> {eventItem.text || "无文本载荷"}
                          </div>
                        ))}
                      </div>
                    </details>
                  ) : null}
                </div>
              </article>
            ))}
          </section>

          <form className="composer" onSubmit={handleSubmit}>
            <div className="composer-frame">
              <textarea
                value={input}
                placeholder="输入你的 12306 问题，例如：候补购票什么时候兑现，开车前还能改签吗"
                onChange={(event) => setInput(event.target.value)}
                disabled={streaming}
              />
              <button className="solid-button" type="submit" disabled={streaming || !input.trim()}>
                {streaming ? "生成中..." : mode === "stream" ? "发送问题" : "同步请求"}
              </button>
            </div>
            <div className="composer-hint">
              <span>{errorText || "前端会自动保存 sessionId，刷新后仍可继续同一会话。"}</span>
              <span>{streaming ? "请求进行中" : mode === "stream" ? "SSE 已就绪" : "同步模式已就绪"}</span>
            </div>
          </form>
        </main>

        <section className="knowledge-panel panel">
          <header className="knowledge-header">
            <div>
              <h2 className="knowledge-title">知识库工作台</h2>
              <p className="knowledge-subtitle">RustFS 存文件，Tika 解析文本，Milvus 存向量，MySQL 存元数据。</p>
            </div>
            <button className="ghost-button compact-button" type="button" onClick={loadDocuments} disabled={loadingDocuments}>
              {loadingDocuments ? "刷新中..." : "刷新列表"}
            </button>
          </header>

          <div className="knowledge-grid">
            <section className="knowledge-card">
              <div className="section-title">导入文档</div>
              <form className="knowledge-form" onSubmit={handleUpload}>
                <label className="field">
                  <span>文档标题</span>
                  <input value={title} onChange={(event) => setTitle(event.target.value)} placeholder="默认使用文件名" />
                </label>
                <label className="field">
                  <span>文档分类</span>
                  <input value={category} onChange={(event) => setCategory(event.target.value)} placeholder="例如：rule / faq / policy" />
                </label>
                <label className="field">
                  <span>选择文件</span>
                  <input ref={fileInputRef} type="file" onChange={(event) => setSelectedFile(event.target.files?.[0] || null)} />
                </label>
                <button className="solid-button wide-button" type="submit" disabled={!selectedFile || uploading}>
                  {uploading ? "上传处理中..." : "上传并入库"}
                </button>
                {uploading ? (
                  <div className="upload-progress-card">
                    <div className="upload-progress-line">
                      <span>传输进度</span>
                      <strong>{uploadProgress}%</strong>
                    </div>
                    <div className="progress-track">
                      <div className="progress-bar" style={{ width: `${uploadProgress}%` }} />
                    </div>
                    <div className="upload-stage-text">
                      {activeDocument?.progressMessage || uploadStageText || "等待后端处理"}
                    </div>
                  </div>
                ) : null}
              </form>
            </section>

            <section className="knowledge-card">
              <div className="section-title">检索测试</div>
              <form className="knowledge-form" onSubmit={handleKnowledgeSearch}>
                <label className="field">
                  <span>检索问题</span>
                  <textarea
                    className="knowledge-query"
                    value={knowledgeQuery}
                    onChange={(event) => setKnowledgeQuery(event.target.value)}
                    placeholder="例如：候补什么时候兑现"
                  />
                </label>
                <button className="solid-button wide-button" type="submit" disabled={!knowledgeQuery.trim() || searchingKnowledge}>
                  {searchingKnowledge ? "检索中..." : "测试召回"}
                </button>
              </form>

              <div className="knowledge-results">
                {knowledgeResults.length === 0 ? (
                  <div className="empty-event">还没有检索结果</div>
                ) : knowledgeResults.map((item, index) => (
                  <article key={`${item.documentId}-${index}`} className="result-card">
                    <div className="result-source">
                      <strong>{item.title || item.documentId}</strong>
                      <span>{item.category || "未分类"}</span>
                    </div>
                    <div className="result-content">{item.content}</div>
                  </article>
                ))}
              </div>
            </section>
          </div>

          <section className="knowledge-card document-card">
            <div className="status-line">
              <span className="section-title">文档列表</span>
              <span className="status-pill">{documents.length}</span>
            </div>
            {documentError ? <div className="error-banner">{documentError}</div> : null}
            <div className="document-table">
              {documents.length === 0 ? (
                <div className="empty-event">还没有导入文档</div>
              ) : documents.map((item) => (
                <div key={item.documentId} className="document-row">
                  <div className="document-main">
                    <strong>{item.title}</strong>
                    <span>{item.fileName}</span>
                  </div>
                  <div className="document-meta">
                    <span>{item.category || "未分类"}</span>
                    <span>{item.parseStatus}</span>
                    <span>{item.chunkCount} chunks</span>
                    <span>{item.uploadedAt ? `上传于 ${formatDateTime(item.uploadedAt)}` : "上传时间未知"}</span>
                  </div>
                  <div className="document-side">
                    <span className="document-progress">{item.progressMessage || "状态已同步"}</span>
                    <button className="ghost-button compact-button" type="button" onClick={() => handleDeleteDocument(item.documentId)}>
                      删除
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </section>
        </section>
      </div>
    </div>
  );
}

function makeEvent(type, text) {
  return {
    id: crypto.randomUUID(),
    type,
    text
  };
}

function MessageText({ message, streaming }) {
  const [displayText, setDisplayText] = useState(() => message.role === "assistant" ? "" : (message.text || ""));
  const textRef = useRef(displayText);
  const timerRef = useRef(null);

  useEffect(() => {
    textRef.current = displayText;
  }, [displayText]);

  useEffect(() => () => {
    if (timerRef.current) {
      window.clearTimeout(timerRef.current);
    }
  }, []);

  useEffect(() => {
    if (timerRef.current) {
      window.clearTimeout(timerRef.current);
      timerRef.current = null;
    }

    if (message.role !== "assistant") {
      setDisplayText(message.text || "");
      return;
    }

    const target = message.text || "";
    if (!target) {
      setDisplayText("");
      return;
    }

    if (!target.startsWith(textRef.current)) {
      textRef.current = "";
      setDisplayText("");
    }

    if (textRef.current === target) {
      return;
    }

    const typeNext = () => {
      const current = textRef.current;
      if (current === target) {
        timerRef.current = null;
        return;
      }
      const remaining = target.length - current.length;
      const step = remaining > 72 ? 8 : remaining > 36 ? 4 : remaining > 12 ? 2 : 1;
      const next = target.slice(0, current.length + step);
      textRef.current = next;
      setDisplayText(next);
      if (next !== target) {
        timerRef.current = window.setTimeout(typeNext, 16);
      } else {
        timerRef.current = null;
      }
    };

    timerRef.current = window.setTimeout(typeNext, 16);
    return () => {
      if (timerRef.current) {
        window.clearTimeout(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [message.id, message.role, message.text]);

  if (!displayText && message.role === "assistant" && streaming) {
    return <span className="typing"><span></span><span></span><span></span></span>;
  }

  const typing = message.role === "assistant" && displayText && displayText !== (message.text || "");

  return (
    <span className={`message-text ${typing ? "typing-active" : ""}`}>
      {displayText}
    </span>
  );
}

async function fetchSessionId(message) {
  const response = await fetch("/api/assistant/chat", {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify({ message })
  });
  if (!response.ok) {
    return "";
  }
  const data = await response.json();
  return data.sessionId || "";
}

function uploadDocument(formData, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("POST", "/api/knowledge/documents");
    xhr.responseType = "json";

    xhr.upload.onprogress = (event) => {
      if (!event.lengthComputable) {
        return;
      }
      const percent = Math.min(100, Math.round((event.loaded / event.total) * 100));
      onProgress(percent);
    };

    xhr.onload = () => {
      const payload = xhr.response;
      if (xhr.status >= 200 && xhr.status < 300) {
        onProgress(100);
        resolve(payload);
        return;
      }
      reject(new Error(payload?.answer || payload?.message || `上传失败: ${xhr.status}`));
    };

    xhr.onerror = () => reject(new Error("上传失败，请检查网络或后端服务"));
    xhr.send(formData);
  });
}

async function consumeSseStream(body, onEvent) {
  const reader = body.getReader();
  const decoder = new TextDecoder("utf-8");
  let buffer = "";

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }

    buffer += decoder.decode(value, { stream: true });
    const chunks = buffer.split("\n\n");
    buffer = chunks.pop() || "";

    chunks.forEach((chunk) => {
      const lines = chunk.split("\n");
      let eventName = "message";
      const dataLines = [];

      lines.forEach((line) => {
        if (line.startsWith("event:")) {
          eventName = line.slice(6).trim();
          return;
        }
        if (line.startsWith("data:")) {
          dataLines.push(line.slice(5).trim());
        }
      });

      if (!dataLines.length) {
        return;
      }

      try {
        onEvent(eventName, JSON.parse(dataLines.join("\n")));
      } catch {
        onEvent("error", { text: "流式事件解析失败" });
      }
    });
  }
}

function formatTime(date) {
  return new Intl.DateTimeFormat("zh-CN", {
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}

function formatDateTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat("zh-CN", {
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  }).format(date);
}
