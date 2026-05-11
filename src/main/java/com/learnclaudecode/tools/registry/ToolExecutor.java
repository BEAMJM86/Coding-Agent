package com.learnclaudecode.tools.registry;

import com.learnclaudecode.permissions.PermissionDecision;
import com.learnclaudecode.permissions.PolicyEngine;
import com.learnclaudecode.tools.hooks.ToolHook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * hook 链执行器。PolicyEngine 作为固定安全层夹在 hook 之间。
 *
 * @author BEAM
 */
public class ToolExecutor {

    private final ToolRegistry registry;
    private final List<ToolHook> globalHooks;
    private final Map<String, List<ToolHook>> toolHooks;
    private final PolicyEngine policyEngine;

    public ToolExecutor(ToolRegistry registry,
                        List<ToolHook> globalHooks,
                        Map<String, List<ToolHook>> toolHooks,
                        PolicyEngine policyEngine) {
        this.registry = registry;
        this.globalHooks = List.copyOf(globalHooks);
        this.toolHooks = Map.copyOf(toolHooks);
        this.policyEngine = policyEngine;
    }

    /**
     * 执行工具调用，经过 hook 链 + PolicyEngine 安全判定。
     */
    public String execute(ToolCall call, ToolContext ctx) {
        // ① PreToolUse hooks（可配置）
        String hookResult = runPreHooks(call, ctx);
        if (hookResult != null) return hookResult;

        // ② PolicyEngine 安全判定（固定层，不可绕过）
        if (policyEngine != null) {
            PermissionDecision decision = policyEngine.decide(call, Map.of());
            if (decision instanceof PermissionDecision.Deny deny) {
                return "Error: " + deny.message();
            }
        }

        // ③ 执行工具
        ToolEntry tool = registry.get(call.name());
        String output;
        if (tool == null) {
            output = "Unknown tool: " + call.name();
        } else {
            output = tool.invoke(call.input());
        }

        // ④ PostToolUse hooks
        output = runPostHooks(call, ctx, output);
        return output;
    }

    private String runPreHooks(ToolCall call, ToolContext ctx) {
        List<ToolHook> chain = new ArrayList<>(globalHooks);
        List<ToolHook> specific = toolHooks.get(call.name());
        if (specific != null) chain.addAll(specific);
        return buildChain(chain, 0, call, ctx);
    }

    private String runPostHooks(ToolCall call, ToolContext ctx, String output) {
        return output;
    }

    private String buildChain(List<ToolHook> hooks, int index,
                               ToolCall call, ToolContext ctx) {
        if (index >= hooks.size()) return null;
        ToolHook hook = hooks.get(index);
        return hook.intercept(call, ctx, () -> buildChain(hooks, index + 1, call, ctx));
    }
}
