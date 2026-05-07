package com.learnclaudecode.tools.hooks;

import com.learnclaudecode.tools.registry.ToolCall;
import com.learnclaudecode.tools.registry.ToolContext;

/**
 * PostToolUse hook：裁剪过长输出，防止超长结果撑爆上下文。
 *
 * @author BEAM
 */
public class OutputTruncator implements ToolHook {

    private final int maxLength;

    public OutputTruncator(int maxLength) {
        this.maxLength = maxLength;
    }

    @Override
    public String intercept(ToolCall call, ToolContext ctx, Proceed next) {
        String result = next.proceed();
        if (result != null && result.length() > maxLength) {
            return result.substring(0, maxLength) + "\n... (truncated)";
        }
        return result;
    }
}
