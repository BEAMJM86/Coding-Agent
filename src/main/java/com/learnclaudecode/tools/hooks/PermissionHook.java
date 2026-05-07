package com.learnclaudecode.tools.hooks;

import com.learnclaudecode.tools.registry.ToolCall;
import com.learnclaudecode.tools.registry.ToolContext;

import java.util.List;

/**
 * PreToolUse hook：基于黑名单规则检查命令安全性。
 *
 * @author BEAM
 */
public class PermissionHook implements ToolHook {

    private static final List<String> DANGEROUS = List.of(
            "rm -rf /", "sudo", "shutdown", "reboot", "> /dev/"
    );

    @Override
    public String intercept(ToolCall call, ToolContext ctx, Proceed next) {
        if ("bash".equals(call.name())) {
            String cmd = call.input("command");
            for (String danger : DANGEROUS) {
                if (cmd.contains(danger)) {
                    return "Error: Dangerous command blocked by security policy";
                }
            }
        }
        return next.proceed();
    }
}
