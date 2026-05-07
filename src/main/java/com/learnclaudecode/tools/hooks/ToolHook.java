package com.learnclaudecode.tools.hooks;

import com.learnclaudecode.tools.registry.ToolCall;
import com.learnclaudecode.tools.registry.ToolContext;

/**
 * 工具执行 hook 函数接口。
 * 调用 next.proceed() 继续链，直接 return 则中断链（PreToolUse 拒绝模式），
 * 在 next.proceed() 之后做处理则为 PostToolUse 模式。
 *
 * @author BEAM
 */
@FunctionalInterface
public interface ToolHook {
    String intercept(ToolCall call, ToolContext ctx, Proceed next);
}
