package com.learnclaudecode.permissions;

import com.learnclaudecode.tools.registry.ToolCall;
import com.learnclaudecode.tools.registry.ToolContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 安全判定总入口，7 步编排：
 * ① deny 规则 → ② 沙箱 → ③ 工具安全检查 → ④ bypass
 * → ⑤ allow 规则/信任记忆 → ⑥ dontAsk → ⑦ 用户确认
 *
 * PolicyEngine 不是 hook，是 ToolExecutor 内部的固定结构层。
 *
 * @author BEAM
 */
public class PolicyEngine {

    private final PermissionRules rules;
    private final TrustStore trustStore;
    private final BashSafetyAnalyzer bashSafety;
    private final FileSafetyChecker fileSafety;
    private final UserConfirmation confirmation;
    private final PermissionMode mode;
    private final boolean shouldAvoidPrompts;
    private final Consumer<String> alternativeInjector;

    private final Map<String, Integer> denialCounts = new ConcurrentHashMap<>();
    private static final int ESCALATION_THRESHOLD = 3;

    // 安全工具白名单 — 只读、非破坏性或纯协调操作，无需确认直接放行
    private static final Set<String> SAFE_TOOLS = Set.of(
            "read_file", "Grep", "Glob", "AskUserQuestion",
            "todo", "TodoWrite",
            // 任务板 — 纯协调，不修改代码文件
            "task_create", "task_update", "task_list", "task_get", "claim_task",
            // 子代理 & 技能
            "subagent", "load_skill",
            // 多 Agent 协作
            "spawn_teammate", "list_teammates", "shutdown_request", "plan_approval",
            "send_message", "read_inbox", "broadcast",
            // 后台 & 工作树
            "check_background", "worktree_list",
            // 消息 & 空闲
            "SendMessage", "idle", "compact"
    );

    public PolicyEngine(PermissionRules rules,
                        TrustStore trustStore,
                        BashSafetyAnalyzer bashSafety,
                        FileSafetyChecker fileSafety,
                        UserConfirmation confirmation,
                        PermissionMode mode,
                        boolean shouldAvoidPrompts,
                        Consumer<String> alternativeInjector) {
        this.rules = rules;
        this.trustStore = trustStore;
        this.bashSafety = bashSafety;
        this.fileSafety = fileSafety;
        this.confirmation = confirmation;
        this.mode = mode;
        this.shouldAvoidPrompts = shouldAvoidPrompts;
        this.alternativeInjector = alternativeInjector;
    }

    /**
     * 工厂方法：从 ToolContext 创建 PolicyEngine。
     */
    public static PolicyEngine create(ToolContext ctx,
                                       PermissionRules rules,
                                       TrustStore trustStore,
                                       BashSafetyAnalyzer bashSafety,
                                       FileSafetyChecker fileSafety,
                                       UserConfirmation confirmation) {
        var config = ctx.config();
        PermissionMode mode = config != null ? config.permissionMode() : PermissionMode.DEFAULT;
        boolean avoidPrompts = mode == PermissionMode.DONT_ASK;
        return new PolicyEngine(rules, trustStore, bashSafety, fileSafety, confirmation,
                mode, avoidPrompts, null);
    }

    /**
     * 统一入口：判定一次工具调用。
     *
     * @return Allow/Deny。Ask 在内部被处理（弹确认或自动拒绝）。
     */
    public PermissionDecision decide(ToolCall call, Map<String, String> readFingerprints) {
        String toolName = call.name();
        String permissionContent = getPermissionContent(call);

        // ① deny 规则 — 最高优先级
        var denyRule = rules.getDenyRule(toolName, permissionContent);
        if (denyRule != null) {
            return new PermissionDecision.Deny(
                    "Denied by rule: " + toolName + "(" + denyRule.content() + ")",
                    DecisionReason.rule("deny"));
        }

        // ② PLAN 模式 — 拒绝所有写操作
        if (mode == PermissionMode.PLAN) {
            if ("bash".equals(toolName) || "write_file".equals(toolName) || "edit_file".equals(toolName)) {
                return new PermissionDecision.Deny(
                        "Write operations not allowed in PLAN mode",
                        DecisionReason.mode("plan"));
            }
        }

        // ③ 沙箱检查（自动放行安全操作）
        if ("bash".equals(toolName)) {
            String cmd = call.input("command");
            BashSafetyAnalyzer.Level level = bashSafety.classify(cmd);
            if (level.autoAllow()) {
                return new PermissionDecision.Allow("Safe command", call.input());
            }
        }

        // ④ 工具安全检查（Allow/Deny 均提前返回，Ask/null 继续后续流程）
        PermissionDecision safetyDecision = checkToolSafety(call);
        if (safetyDecision instanceof PermissionDecision.Deny) return safetyDecision;
        if (safetyDecision instanceof PermissionDecision.Allow) return safetyDecision;

        // ④.5 安全工具白名单 — 只读/非破坏性工具自动放行
        if (SAFE_TOOLS.contains(toolName)) {
            return new PermissionDecision.Allow("Safe tool", call.input());
        }

        // ⑤ BYPASS 模式
        if (mode == PermissionMode.BYPASS) {
            return new PermissionDecision.Allow("Bypass mode", call.input());
        }

        // ⑥ allow 规则 + 信任记忆
        if (trustStore.isAllowed(toolName, permissionContent)) {
            return new PermissionDecision.Allow("Always allowed", call.input());
        }

        // ACCEPT_EDITS 模式：自动放行编辑工具
        if (mode == PermissionMode.ACCEPT_EDITS
                && ("write_file".equals(toolName) || "edit_file".equals(toolName))) {
            return new PermissionDecision.Allow("Accept edits mode", call.input());
        }

        // ⑦ DONT_ASK 模式
        if (shouldAvoidPrompts || mode == PermissionMode.DONT_ASK) {
            return new PermissionDecision.Deny(
                    "Permission denied: prompts not available",
                    DecisionReason.mode("dontAsk"));
        }

        // ⑧ 用户确认
        String detail = buildDetail(call);
        UserConfirmation.Choice choice = confirmation.ask(toolName, detail, permissionContent);

        return switch (choice) {
            case ALLOW_ONCE -> new PermissionDecision.Allow("User allowed once", call.input());
            case ALLOW_ALWAYS -> new PermissionDecision.Allow("User allowed always", call.input());
            case DENY -> {
                recordDenial(toolName);
                yield new PermissionDecision.Deny("User denied", DecisionReason.userDenied());
            }
            case TELL_ALTERNATIVE ->
                new PermissionDecision.Deny("User provided alternative",
                        DecisionReason.userDenied());
        };
    }

    private PermissionDecision checkToolSafety(ToolCall call) {
        return switch (call.name()) {
            case "bash" -> {
                String cmd = call.input("command");
                yield bashSafety.checkPermission(cmd);
            }
            case "write_file", "edit_file" -> {
                String path = getFilePath(call);
                yield fileSafety.checkBeforeWrite(path, null);
            }
            default -> null;
        };
    }

    /**
     * 模型可能使用 path 或 file_path 作为写文件参数名，两者都尝试。
     */
    private String getFilePath(ToolCall call) {
        String p = call.input("file_path");
        if (p.isEmpty()) p = call.input("path");
        return p;
    }

    private String getPermissionContent(ToolCall call) {
        return switch (call.name()) {
            case "bash" -> call.input("command");
            case "write_file", "edit_file", "read_file" -> getFilePath(call);
            default -> "";
        };
    }

    private String buildDetail(ToolCall call) {
        return switch (call.name()) {
            case "bash" -> call.input("command");
            case "write_file" -> "write: " + getFilePath(call);
            case "edit_file" -> "edit: " + getFilePath(call);
            default -> call.name();
        };
    }

    private void recordDenial(String toolName) {
        int count = denialCounts.merge(toolName, 1, Integer::sum);
        if (count >= ESCALATION_THRESHOLD) {
            System.out.println("[!] 该命令已被拒绝 " + count + " 次，建议永久禁止。");
        }
    }

    // -- 访问器（供子代理复用组件） --

    public PermissionRules getRules() { return rules; }
    public TrustStore getTrustStore() { return trustStore; }
    public BashSafetyAnalyzer getBashSafety() { return bashSafety; }
    public FileSafetyChecker getFileSafety() { return fileSafety; }
}
