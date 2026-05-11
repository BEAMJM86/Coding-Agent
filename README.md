# Coding Agent

基于 Anthropic API 的 Claude Code 风格编码 Agent，Java 17 实现。

**LLM 决策 + 本地运行时执行工具 + 循环直到任务完成。**

## 快速开始

```bash
# 1. 配置 API 密钥
cp .env.example .env   # 编辑 ANTHROPIC_API_KEY, MODEL_ID, ANTHROPIC_BASE_URL

# 2. 构建
mvn compile

# 3. 运行
mvn exec:java -Dexec.mainClass=com.learnclaudecode.agents.SFull
```

环境要求：Java 17+、Maven 3.8+。

## 核心架构

```
用户输入 → AgentRuntime 写入消息历史
         → AnthropicClient 调用模型 (system + messages + 25 tools)
         → 模型返回 tool_use → PolicyEngine 安全判定 → ToolExecutor 执行
         → 工具结果回写消息历史 → 再次调用模型
         → 循环直到模型返回文本（不再请求工具）
```

### 关键组件

| 组件 | 职责 |
|------|------|
| `AgentRuntime` | Agent 主循环、工具调用分发、子代理执行 |
| `StageConfig` | 系统提示词、功能开关、能力配置 |
| `AppContext` | 应用装配器，创建并连接所有共享服务 |
| `PolicyEngine` | 7+1 步安全判定管线（拒绝规则→PLAN→沙箱→安全检查→白名单→信任记忆→用户确认） |
| `ToolExecutor` | 工具执行器，hook 链 + PolicyEngine 前置判定 |
| `AnthropicClient` | Anthropic Messages API 封装 |
| `CompressionService` | 三层上下文压缩（micro/auto/manual） |

### 权限系统

```
PolicyEngine.decide() 判定流程：
  ① deny 规则（最高优先级）
  ② PLAN 模式拒绝写操作
  ③ 沙箱检查（安全命令自动放行）
  ④ 工具安全检查（Bash 6 级分类 + 文件保护）
  ④.5 安全工具白名单（25+ 只读/协调工具自动放行）
  ⑤ BYPASS 模式
  ⑥ allow 规则 + 信任记忆（TrustStore）
  ⑦ ACCEPT_EDITS 模式
  ⑧ 用户确认（4 选项对话框）

新文件自动放行：write_file 对不存在文件跳过确认链，保护目录/文件除外。
```

## 工具总览（25 个）

### 文件与命令
| 工具 | 说明 |
|------|------|
| `bash` | 执行 shell 命令，6 级安全分类（safe→dangerous） |
| `read_file` | 读取文件内容，返回行号格式 |
| `write_file` | 创建或覆写文件，新文件自动放行 |
| `edit_file` | 精确字符串匹配替换，只替换首次出现 |
| `Glob` | 文件通配搜索（Java NIO.2），返回修改时间排序 |
| `Grep` | 正则内容搜索（ripgrep 风格），支持 glob/type 过滤 |

### 任务管理
| 工具 | 说明 |
|------|------|
| `todo` | 个人短期清单，跟踪自己执行的步骤 |
| `task_create` | 在共享任务板创建任务 |
| `task_update` | 更新任务状态和依赖关系 |
| `task_list` | 列出共享板上所有任务 |
| `task_get` | 获取单个任务详情 |
| `claim_task` | 认领未分配任务 |

### 子代理与协作
| 工具 | 说明 |
|------|------|
| `subagent` | 启动独立上下文子代理，同步执行并返回摘要 |
| `spawn_teammate` | 创建持久化队友 Agent |
| `list_teammates` | 列出所有队友及其状态 |
| `shutdown_request` | 向队友发送关闭信号 |
| `plan_approval` | 响应队友的计划审批请求 |
| `send_message` | 向指定队友发送消息 |
| `read_inbox` | 读取并消费收件箱消息 |
| `broadcast` | 向所有活跃队友广播消息 |

### 后台与隔离
| 工具 | 说明 |
|------|------|
| `background_run` | 异步后台执行命令 |
| `check_background` | 查询后台任务结果 |
| `worktree_create` | 为任务创建隔离 git worktree |
| `worktree_list` | 列出所有 worktree |
| `worktree_remove` | 删除 worktree |
| `worktree_events` | 查看 worktree 生命周期事件 |

### 其他
| 工具 | 说明 |
|------|------|
| `load_skill` | 加载技能文档 |
| `AskUserQuestion` | 终端交互式向用户提问（1-4 问题，单选/多选） |

## 目录结构

```text
src/main/java/com/learnclaudecode/
├── agents/          # AgentRuntime, StageConfig, AppContext, 入口
├── permissions/     # PolicyEngine, BashSafetyAnalyzer, FileSafetyChecker,
│                    # TrustStore, UserConfirmation, PermissionRules
├── tools/           # 工具实现 + @AgentTool 注解 + ToolExecutor + hook 链
│                    # CommandTools, TaskTools, TeammateTools, MessageBusTools,
│                    # BackgroundTools, WorktreeTools, Glob/Grep/AskUserQuestion
├── tasks/           # TaskManager, WorktreeManager
├── team/            # TeammateManager, MessageBus (文件型 inbox)
├── background/      # BackgroundManager (后台任务线程池)
├── context/         # CompressionService (上下文压缩)
├── common/          # AnthropicClient, WorkspacePaths, EnvConfig, JsonUtils
└── model/           # ChatMessage 数据模型
```

## 设计原则

- **工具是 Agent 的执行器官** — 模型只负责决策，本地 Java 代码真正执行
- **消息历史是工作记忆** — 用户输入、工具结果、后台通知都进入消息历史
- **长任务需要状态外置** — Todo（个人）、Task（共享）、Worktree（隔离）逐级扩展
- **多 Agent 协作 = 协议 + 状态 + 通信** — 文件 inbox + JSON 任务板，无需消息队列
- **安全分层治理** — 7 步判定管线，沙箱自动分类、白名单放行、信任记忆、用户兜底
