package com.learnclaudecode.permissions;

/**
 * --dangerously-skip-permissions 安全门。
 * root + 非沙箱 → 拒绝启动。
 * Windows 跳过 root 检查（无 uid 0 概念）。
 *
 * @author BEAM
 */
public class BypassSafetyGate {

    private static final String IS_SANDBOX_ENV = "IS_SANDBOX";
    private static final String CLAUDE_CODE_BUBBLEWRAP = "CLAUDE_CODE_BUBBLEWRAP";

    /**
     * 检查是否可以启用 bypass 模式。
     *
     * @return true 可以 bypass，false 拒绝启动
     */
    public boolean canBypass() {
        boolean isSandbox = "1".equals(System.getenv(IS_SANDBOX_ENV))
                || System.getenv(CLAUDE_CODE_BUBBLEWRAP) != null;

        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().contains("win");
        if (isWindows) return true;

        String user = System.getProperty("user.name");
        if ("root".equals(user) && !isSandbox) {
            System.err.println("DANGER: root user detected outside sandbox. "
                    + "Bypass mode is not allowed in this configuration.");
            return false;
        }
        return true;
    }
}
