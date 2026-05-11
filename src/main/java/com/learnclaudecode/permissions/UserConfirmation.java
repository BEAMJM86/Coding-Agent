package com.learnclaudecode.permissions;

import java.util.Scanner;
import java.util.function.Consumer;

/**
 * 终端用户确认交互。
 * 4 个选项：放行一次 / 总是放行（本项目） / 拒绝 / 告诉 Claude 改做什么。
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
     * @param detail    操作详情
     * @param permissionContent 用于持久化的规则内容
     */
    public Choice ask(String toolName, String detail, String permissionContent) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║  Security Check                      ║");
        System.out.println("╠══════════════════════════════════════╣");
        System.out.printf("║  %-36s ║%n", toolName + ": " + truncate(detail, 28));
        System.out.println("╠══════════════════════════════════════╣");
        System.out.println("║  1. 放行一次                          ║");
        System.out.println("║  2. 总是放行（本项目）                 ║");
        System.out.println("║  3. 拒绝                              ║");
        System.out.println("║  4. 告诉 Claude 改做什么              ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.print("选择 [1-4]: ");

        while (true) {
            String input = scanner.nextLine().trim();
            switch (input) {
                case "1": return Choice.ALLOW_ONCE;
                case "2":
                    trustStore.addAllow(toolName, permissionContent);
                    return Choice.ALLOW_ALWAYS;
                case "3": return Choice.DENY;
                case "4":
                    System.out.print("请输入替代指令: ");
                    String alt = scanner.nextLine().trim();
                    if (alternativeHandler != null && !alt.isEmpty()) {
                        alternativeHandler.accept(alt);
                    }
                    return Choice.TELL_ALTERNATIVE;
                default:
                    System.out.print("无效选择，请重新输入 [1-4]: ");
            }
        }
    }

    private String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
