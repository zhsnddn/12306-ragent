# 🚄 12306-RAgent

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-17-blue.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

> 12306 智能票务助手

## 📖 项目简介

12306-RAgent 是一个采用 **AgentScope** 多智能体框架构建的智能票务问答系统，通过**树形意图识别**精准理解用户需求，结合 **Plan 编排**与 **RAG 知识检索**，为用户提供全面的火车票查询与规则咨询服务。

### 核心特性

- 🎯 **树形意图识别** - 三层结构（Domain → Category → Topic）精准匹配用户问题
- 🔄 **Plan 编排执行** - 结构化任务分解与执行流程
- 📚 **RAG 知识增强** - 基于 Milvus 向量数据库的规则知识检索
- 🔌 **MCP 工具调用** - 实时对接 12306 MCP 票务服务
- 🔐 **SSO 单点登录** - 企业级身份认证集成
- 💬 **流式对话输出** - SSE 实时推送用户体验

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                        用户请求                              │
│                   /api/assistant/chat                       │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                    Intent Resolver                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ Intent Tree  │→ │ Classifier   │→ │ Guidance/Fallback│  │
│  │  意图树      │  │ LLM 分类器   │  │ 歧义引导/兜底    │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────┬───────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────┐
│                 Travel Planning Service                      │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │Extract Intent│  │Query Ticket  │  │Retrieve Knowledge │  │
│  │  提取参数    │  │  票务查询    │  │  知识检索        │  │
│  └──────────────┘  └──────────────┘  └──────────────────┘  │
└─────────────────────────┬───────────────────────────────────┘
                          │
         ┌────────────────┼────────────────┐
         │                │                │
         ▼                ▼                ▼
   ┌──────────┐    ┌──────────┐    ┌──────────┐
   │   MCP    │    │  RAG     │    │  Memory  │
   │ 12306票务│    │ Milvus   │    │ 会话记忆 │
   └──────────┘    └──────────┘    └──────────┘
```

## 📁 项目结构

```
src/main/java/com/ming/agent12306/
├── config/                    # 配置类
├── controller/                 # 控制器
├── common/                     # 公共组件
│   ├── constant/              # 常量定义
│   ├── preprocess/            # 预处理
│   ├── util/                  # 工具类
│   └── validation/            # 参数校验
├── intent/                    # 🎯 意图识别模块
│   ├── model/                # 数据模型
│   ├── config/               # 配置类
│   ├── service/              # 服务层
│   ├── resolver/             # 解析器
│   └── fallback/             # 兜底响应
├── knowledge/                 # 📚 知识库模块
│   ├── entity/               # 实体类
│   ├── mapper/               # 数据访问
│   ├── model/                # 数据模型
│   └── service/              # 服务层
├── memory/                    # 💬 会话记忆模块
│   ├── entity/               # 实体类
│   ├── mapper/               # 数据访问
│   ├── model/                # 数据模型
│   └── service/              # 服务层
├── plan/                      # 🔄 Plan编排模块
│   ├── model/                # 数据模型
│   ├── service/              # 服务层
│   └── aop/                  # 切面
├── properties/                # 配置属性
├── service/                   # 核心服务
└── sso/                       # 🔐 单点登录
    ├── controller/            # 控制器
    ├── interceptor/          # 拦截器
    └── model/                 # 数据模型
```

## 🚀 快速开始

### 环境要求

| 组件 | 版本要求 |
|------|----------|
| JDK | 17+ |
| Maven | 3.8+ |
| MySQL | 8.0+ |
| Redis | 6.0+ |
| Milvus | 2.3+ (可选) |

### 1. 克隆项目

```bash
git clone https://github.com/your-repo/12306-ragent.git
cd 12306-ragent
```

### 2. 配置数据库

编辑 `src/main/resources/application.yaml` 或设置环境变量：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/agent12306
    username: root
    password: your_password
  data:
    redis:
      host: 127.0.0.1
      port: 6379
      password: your_redis_password
```

### 3. 启动依赖服务

```bash
# MySQL
docker run -d --name mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=123456 \
  mysql:8.0

# Redis
docker run -d --name redis -p 6379:6379 \
  redis:7-alpine

# Milvus (可选，用于知识库向量检索)
docker run -d --name milvus -p 19530:19530 \
  milvusdb/milvus:v2.3.0
```

### 4. 编译运行

```bash
# 编译
mvn clean package -DskipTests

# 运行
mvn spring-boot:run

# 或者运行打包后的 JAR
java -jar target/12306-ragent-0.0.1-SNAPSHOT.jar
```


## ⚙️ 配置说明

### 意图识别配置

```yaml
assistant:
  intent:
    min-score: 0.35              # 最低分数阈值
    max-intent-count: 3          # 最大意图数量
    guidance:
      enabled: true              # 启用歧义引导
      ambiguity-score-ratio: 0.8 # 歧义比例
    cache:
      intent-tree-key: "12306:intent:tree"
      ttl-days: 7                # 缓存过期天数
```

### MCP 票务工具配置

```yaml
assistant:
  mcp:
    enabled: true
    name: ticketing-mcp
    command: java
    jar-path: ../12306-mcp/target/12306-mcp-0.0.1-SNAPSHOT.jar
    timeout-seconds: 30
```

### 调试模式

开启调试模式可跳过 SSO 登录验证：

```yaml
sso:
  debug-mode: true   # 默认为 true，用于开发调试
```

## 🎯 意图树结构

系统采用三层树形结构组织意图：

```
12306票务系统 (Domain)
├── 票务查询 (Category)
│   ├── 站点余票查询 [MCP]
│   ├── 车次经停站查询 [MCP]
│   └── 换乘方案查询 [MCP]
├── 规则咨询 (Category)
│   ├── 退票规则 [KB]
│   ├── 改签规则 [KB]
│   ├── 候补购票规则 [KB]
│   ├── 购票限制说明 [KB]
│   ├── 学生票规则 [KB]
│   └── 积分规则 [KB]
├── 业务办理 (Category)
│   ├── 订单查询 [MCP]
│   └── 取消订单 [MCP]
├── 账户服务 (Category)
│   ├── 会员等级权益 [KB]
│   └── 乘车人管理 [MCP]
└── 投诉建议 (Category)
    └── 投诉建议 [SYSTEM]
```

**意图类型说明：**
- **KB** (Knowledge Base) - 知识库检索
- **MCP** (Model Context Protocol) - 外部工具调用
- **SYSTEM** - 系统预定义交互

## 📡 API 接口

### 对话接口

```http
POST /api/assistant/chat
Content-Type: application/json

{
  "sessionId": "optional-session-id",
  "message": "查一下明天北京到上海的高铁票"
}
```

### 流式对话

```http
POST /api/assistant/chat/stream
Content-Type: application/json

{
  "sessionId": "optional-session-id",
  "message": "退票怎么收费"
}
```

### 响应示例

```json
{
  "success": true,
  "sessionId": "sess_1234567890",
  "answer": "根据您的问题，为您查询到以下结果...",
  "structuredAnswer": {
    "summary": "明天北京到上海有多趟高铁可选",
    "ticketHighlights": ["G1234 二等座余票充足", "复兴号系列"],
    "recommendations": ["建议提前购买以确保座位"],
    "ruleTips": []
  }
}
```

## 🔧 开发指南

### 添加新的意图节点

1. 在 `IntentTreeService.buildIntentTree()` 中添加节点定义：

```java
leafNodes.add(buildTopicNode(
    "cat_ticket/topic_new_intent",  // 唯一ID
    "新意图名称",                    // 名称
    "意图描述",                      // 描述
    IntentKind.MCP,                 // 意图类型
    List.of("示例问题1", "示例问题2"), // 示例
    catTicket                       // 父节点
));
```

2. 在对应的 Service 中实现处理逻辑

### 扩展兜底响应

编辑 `FallbackResponseService.java` 中的模式匹配规则：

```java
// 添加新的模式
private static final List<Pattern> NEW_PATTERNS = List.of(
    Pattern.compile("你的关键词", Pattern.CASE_INSENSITIVE)
);
```

## 📝 项目日志

查看运行时日志：

```bash
tail -f logs/spring.log
```

关键日志前缀：
- `[intent]` - 意图识别相关
- `[plan]` - Plan 编排执行
- `[kb]` - 知识库检索
- `[auth]` - 认证授权

## 🧪 测试

```bash
# 运行所有测试
mvn test

# 运行特定测试类
mvn test -Dtest=IntentResolverTest

# 生成测试覆盖率报告
mvn test jacoco:report
```

## 📦 知识库管理

### 上传知识文档

```http
POST /api/knowledge/documents/upload
Content-Type: multipart/form-data

file: [文档文件]
category: "退票规则"
```

### 搜索知识

```http
GET /api/knowledge/search?query=退票规则
```


## 📄  说明
仅供学习使用，添加了解到的方案
