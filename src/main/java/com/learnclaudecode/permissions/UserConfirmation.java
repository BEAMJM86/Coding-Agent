package com.learnclaudecode.permissions;

import java.util.Scanner;
import java.util.function.Consumer;

/**
 * 终端用户确认交互 — Claude Code 风格。
 * 4 个选项：Yes / Yes + 总是放行 / No / No + 告诉替代方案。
 *
 * @author BEAM
 */
public class UserConfirmation {

    public enum Choice {
        ALLOW_ONCE, ALLOW_ALWAYS, DENY, TELL_ALTERNATIVE
    }

    private final Scanner scanner;
    private final TrustStore trustStore;
    private Consumer<String> alternativeHandler;

    public UserConfirmation(Scanner scanner, TrustStore trustStore) {
        this.scanner = scanner;
        this.trustStore = trustStore;
    }

    public void setAlternativeHandler(Consumer<String> handler) {
        this.alternativeHandler = handler;
    }

    /**
     * 展示确认对话框，返回用户选择。
     *
     * @param toolName  工具名
     * @param detail    操作详情（完整命令/文件路径）
     * @param permissionContent 用于持久化的规则内容
     */
    public Choice ask(String toolName, String detail, String permissionContent) {
        String prefix = permissionPrefix(toolName, permissionContent);

        System.out.println();
        System.out.println("Do you want to proceed?");
        System.out.println("  " + toolName + " " + detail);
        System.out.println();
        System.out.println("  1. Yes");
        System.out.println("  2. Yes, and don't ask again for " + prefix + " sessions");
        System.out.println("  3. No");
        System.out.println("  4. No, and tell Claude what to do instead");
        System.out.println();
        System.out.print("Choice [1-4]: ");

        while (true) {
            String input = scanner.nextLine().trim();
            switch (input) {
                case "1": return Choice.ALLOW_ONCE;
                case "2":
                    trustStore.addAllow(toolName, permissionContent);
                    System.out.println("  >> Saved to .coding-agent/settings.local.json");
                    return Choice.ALLOW_ALWAYS;
                case "3": return Choice.DENY;
                case "4":
                    System.out.print("Give it different instructions: ");
                    String alt = scanner.nextLine().trim();
                    if (alternativeHandler != null && !alt.isEmpty()) {
                        alternativeHandler.accept(alt);
                    }
                    return Choice.TELL_ALTERNATIVE;
                default:
                    System.out.print("Invalid choice, enter [1-4]: ");
            }
        }
    }

    /**
     * 计算配置文件中保存的规则前缀（用于选项 2 展示）。
     */
    static String permissionPrefix(String toolName, String permissionContent) {
        if (permissionContent == null || permissionContent.isEmpty()) {
            return toolName + "(*)";
        }
        if ("bash".equals(toolName)) {
            String baseCmd = permissionContent.trim().split("\\s+")[0];
            return toolName + "(" + baseCmd + ":*)";
        }
        return toolName + "(" + permissionContent + ")";
    }
}
