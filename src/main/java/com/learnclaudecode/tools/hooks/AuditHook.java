package com.learnclaudecode.tools.hooks;

import com.learnclaudecode.tools.registry.ToolCall;
import com.learnclaudecode.tools.registry.ToolContext;
import lombok.extern.slf4j.Slf4j;

/**
 * PreToolUse + PostToolUse hook：记录每次工具调用的输入、耗时和输出。
 *
 * @author BEAM
 */
@Slf4j
public class AuditHook implements ToolHook {

    @Override
    public String intercept(ToolCall call, ToolContext ctx, Proceed next) {
        log.info("TOOL_START name={} input={}", call.name(), call.input());
        long start = System.currentTimeMillis();
        try {
            String result = next.proceed();
            long elapsed = System.currentTimeMillis() - start;
            log.info("TOOL_END name={} elapsed={}ms output_len={}",
                    call.name(), elapsed, result != null ? result.length() : 0);
            return result;
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("TOOL_ERROR name={} elapsed={}ms error={}", call.name(), elapsed, e.getMessage());
            throw e;
        }
    }
}
