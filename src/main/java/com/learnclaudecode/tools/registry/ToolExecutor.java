package com.learnclaudecode.tools.registry;

import com.learnclaudecode.tools.hooks.ToolHook;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * hook 链执行器。将 global hooks 和 tool-specific hooks 串联后执行。
 *
 * @author BEAM
 */
public class ToolExecutor {

    private final ToolRegistry registry;
    private final List<ToolHook> globalHooks;
    private final Map<String, List<ToolHook>> toolHooks;

    public ToolExecutor(ToolRegistry registry,
                        List<ToolHook> globalHooks,
                        Map<String, List<ToolHook>> toolHooks) {
        this.registry = registry;
        this.globalHooks = List.copyOf(globalHooks);
        this.toolHooks = Map.copyOf(toolHooks);
    }

    /**
     * 执行工具调用，经过完整 hook 链。
     */
    public String execute(ToolCall call, ToolContext ctx) {
        List<ToolHook> chain = new ArrayList<>(globalHooks);
        List<ToolHook> specific = toolHooks.get(call.name());
        if (specific != null) {
            chain.addAll(specific);
        }
        return buildChain(chain, 0, call, ctx);
    }

    private String buildChain(List<ToolHook> hooks, int index,
                               ToolCall call, ToolContext ctx) {
        if (index >= hooks.size()) {
            ToolEntry tool = registry.get(call.name());
            return tool != null ? tool.invoke(call.input()) : "Unknown tool: " + call.name();
        }
        ToolHook hook = hooks.get(index);
        return hook.intercept(call, ctx, () -> buildChain(hooks, index + 1, call, ctx));
    }
}
