# Coding Agent

一个用 Java 实现的 Claude Code 风格 Agent。

核心思想：**LLM 负责决策** + **本地运行时负责执行工具** + **循环直到任务完成**。

项目设计参考了 [shareAI-lab/learn-claude-code](https://github.com/shareAI-lab/learn-claude-code)。

## 核心架构

```
用户输入 -> AgentRuntime 写入消息历史
         -> AnthropicClient 调用大模型 (system + messages + tools)
         -> 模型返回 tool_use 或文本
         -> 如果是 tool_use，本地执行工具
         -> 工具结果回写消息历史
         -> 再次调用模型
         -> 循环直到模型不再请求工具
```

## 目录结构

```text
coding-agent/
├─ src/main/java/com/learnclaudecode/
│  ├─ agents/           # 入口、运行时、装配、配置
│  ├─ common/           # API 客户端、环境配置、路径管理、JSON 工具
│  ├─ context/          # 上下文压缩
│  ├─ model/            # 数据模型
│  ├─ tools/            # 命令执行、文件读写、Todo 管理
│  ├─ tasks/            # 任务系统、Worktree 管理
│  ├─ team/             # 消息总线、队友管理
│  ├─ background/       # 后台任务
│  └─ skills/           # 技能加载
├─ skills/              # 技能文档（SKILL.md）
├─ web/                 # 前端项目（Next.js）
├─ .env.example
├─ pom.xml
└─ README.md
```

### 关键类

- **`AgentRuntime`** — Agent 主循环，工具调用分发，子代理执行
- **`StageConfig`** — 能力配置（工具集、system prompt、功能开关）
- **`AppContext`** — 应用装配器，创建并连接所有共享服务
- **`Launcher`** — 统一入口启动器
- **`AnthropicClient`** — 大模型 API 调用
- **`CommandTools`** — bash 执行、文件读写
- **`CompressionService`** — 三层上下文压缩（micro compact / auto compact / manual compact）
- **`TaskManager`** — 文件任务板（创建、更新、认领、依赖）
- **`TeammateManager`** — 多 Agent 队友生命周期管理
- **`MessageBus`** — 文件型消息总线（inbox JSONL）
- **`WorktreeManager`** — 任务隔离工作区

## 能力总览

| 能力 | 工具 |
|------|------|
| 基础执行 | `bash`, `read_file`, `write_file`, `edit_file` |
| 任务规划 | `todo`, `task_create`, `task_update`, `task_list`, `task_get` |
| 子代理 | `task`（独立上下文的子 Agent） |
| 技能加载 | `load_skill` |
| 上下文压缩 | `compact`（手动触发） |
| 后台任务 | `background_run`, `check_background` |
| 多 Agent 协作 | `spawn_teammate`, `send_message`, `read_inbox`, `broadcast`, `list_teammates` |
| 团队协议 | `shutdown_request`, `plan_approval` |
| 自治队友 | `claim_task`, `idle` |
| Worktree 隔离 | `worktree_create`, `worktree_list`, `worktree_remove`, `worktree_events` |

## 运行前准备

### 1. 配置 `.env`

复制 `.env.example` 为 `.env`，配置以下变量：

```env
ANTHROPIC_API_KEY=your_api_key
MODEL_ID=your_model_name
ANTHROPIC_BASE_URL=https://your-provider.example.com/api/anthropic
```

### 2. 环境要求

- Java 17
- Maven

## 构建与运行

```bash
# 构建
mvn compile

# 运行
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.SFull
```

### 运行前端（可选）

```bash
cd web
npm install
npm run dev
# 访问 http://localhost:3000
```

## 设计原则

### 运行时与能力配置分离

`AgentRuntime` 只负责"怎么跑"，`StageConfig` 负责"能做什么"。Agent 的能力由运行时配置决定，而非写死在代码里。

### 工具是 Agent 的执行器官

大模型只负责决策。执行命令、读写文件、管理任务、收发消息 — 这些都是本地 Java 代码真正做的。

### 消息历史是 Agent 的工作记忆

用户输入、工具结果、队友消息、后台任务结果都会进入消息历史。Agent 每一轮都依赖已有 messages 做决策。

### 长任务需要状态外置

Todo、Task、Worktree、Team 这些能力都在解决同一个问题：当任务变长、变复杂、变多人协作时，Agent 不能只靠一轮对话记住所有事情。

### 多 Agent 协作 = 协议 + 状态 + 通信

用文件 inbox、JSON 任务板、状态字段和简单协议实现多 Agent 协作，无需复杂消息队列。
