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
    title: "记忆测试",
    message: "记住我偏好上午出发、二等座、预算 600 以内",
    note: "第二轮继续问查票，观察 session 记忆"
  }
];

const DEBUG_EVENT_TYPES = new Set(["reasoning", "tool_result", "summary", "prompt", "error"]);

export default function App() {
  const [sessionId, setSessionId] = useState(() => window.localStorage.getItem(STORAGE_KEY) || "");
  const [messages, setMessages] = useState([]);
  const [input, setInput] = useState("帮我查一下明天北京到上海的高铁车票，优先二等座");
  const [streaming, setStreaming] = useState(false);
  const [health, setHealth] = useState("检查中");
  const [errorText, setErrorText] = useState("");
  const [mode, setMode] = useState("stream");
  const [eventLog, setEventLog] = useState([]);
  const abortRef = useRef(null);
  const scrollRef = useRef(null);

  useEffect(() => {
    fetch("/api/assistant")
      .then((response) => response.text())
      .then(() => setHealth("服务已连接"))
      .catch(() => setHealth("服务未连接"));
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

  useEffect(() => () => abortRef.current?.abort(), []);

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

  const messageCount = messages.filter((item) => item.role === "user").length;

  return (
    <div className="app-shell">
      <aside className="sidebar panel">
        <div className="brand">
          <span className="brand-eyebrow">React Client</span>
          <div className="brand-title">12306 Assistant</div>
          <p className="brand-copy">
            独立 React 子项目，专门用于联调流式回复、多轮对话和 MySQL + Milvus 记忆链路。
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
            <span className="status-pill">3 条样例</span>
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

      <main className="chat-panel panel">
        <header className="chat-header">
          <div>
            <h1 className="chat-title">多轮记忆调试台</h1>
            <p className="chat-subtitle">
              前端走独立 Vite 开发服务器，接口通过代理转发到 Spring Boot。
            </p>
          </div>
          <div className="chat-status">{streaming ? "模型正在生成" : "等待输入"}</div>
        </header>

        <section className="messages" ref={scrollRef}>
          {messages.length === 0 ? (
            <div className="welcome-card">
              <h2>先提第一轮，再连续追问</h2>
              <p>
                第一轮会建立 session。第二轮开始继续问同一件事，才能真实观察滑动窗口、摘要和长期记忆召回。
              </p>
            </div>
          ) : null}

          {messages.map((message) => (
            <article key={message.id} className={`message ${message.role}`}>
              <div className="avatar">{message.role === "user" ? "YOU" : "AI"}</div>
              <div className="bubble-wrap">
                <div className="bubble">
                  {message.text || (message.role === "assistant" && streaming ? (
                    <span className="typing"><span></span><span></span><span></span></span>
                  ) : null)}
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
              placeholder="输入你的 12306 问题，例如：帮我查一下明天北京到上海的高铁车票，优先二等座"
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
