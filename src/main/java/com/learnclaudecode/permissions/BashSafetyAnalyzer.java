package com.learnclaudecode.permissions;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Bash 命令安全等级分类器。
 * 将命令分为 6 级：safe → read_only → write → destructive → dangerous → unknown。
 *
 * @author BEAM
 */
public class BashSafetyAnalyzer {

    public enum Level {
        SAFE,       // ls, cat, echo, pwd, which, type
        READ_ONLY,  // git status, git diff, grep, find, wc
        WRITE,      // git commit, npm install, mkdir, touch, cp, mv
        DESTRUCTIVE,// git push --force, rm, rmdir, truncate
        DANGEROUS,  // sudo, curl | bash, eval, chmod 777
        UNKNOWN;    // 复杂管道/脚本/未知命令

        public boolean autoAllow() { return this == SAFE || this == READ_ONLY; }
    }

    private static final Set<String> SAFE_CMDS = Set.of(
            "ls", "dir", "echo", "cat", "head", "tail", "pwd", "cd",
            "which", "where", "type", "date", "time", "hostname", "whoami",
            "id", "groups", "env", "printenv", "uname", "arch",
            // Windows PowerShell
            "get-childitem", "get-content", "get-location", "write-output",
            "get-date", "get-host", "get-process", "get-service"
    );

    private static final Set<String> READ_ONLY_CMDS = Set.of(
            "git", "grep", "find", "wc", "sort", "uniq", "diff", "cmp",
            "du", "df", "free", "ps", "top", "htop", "uptime", "stat",
            "file", "tree", "less", "more", "man", "info",
            // Windows PowerShell
            "select-string", "get-command", "get-help", "out-file",
            "test-path", "resolve-path", "get-item"
    );

    private static final Set<String> GIT_DESTRUCTIVE = Set.of(
            "push", "reset", "clean", "stash"
    );

    private static final Set<String> WRITE_CMDS = Set.of(
            "mkdir", "touch", "cp", "mv", "ln", "tar", "zip", "unzip",
            "npm", "yarn", "pnpm", "pip", "cargo", "go", "mvn", "gradle",
            "docker", "kubectl", "helm",
            // Windows PowerShell
            "new-item", "copy-item", "move-item", "set-content",
            "add-content", "ni"
    );

    private static final Set<String> DESTRUCTIVE_CMDS = Set.of(
            "rm", "rmdir", "truncate", "del",
            // Windows PowerShell
            "remove-item", "ri"
    );

    private static final Set<String> DANGEROUS_CMDS = Set.of(
            "sudo", "su", "shutdown", "reboot", "halt", "poweroff",
            "mkfs", "dd", "fdisk", "parted", "mount", "umount",
            "chmod", "chown", "chgrp", "setfacl",
            // Windows PowerShell
            "stop-computer", "restart-computer", "invoke-expression",
            "iex", "start-process"
    );

    private static final List<String> DANGEROUS_PATTERNS = List.of(
            "sudo", "> /dev/", "curl", "wget", "eval", "exec",
            ":(){ :|:& };:",
            "rm -rf /", "rm -rf --no-preserve-root",
            "chmod 777 /", "chmod -R 777",
            "mkfs.", "dd if=", "/dev/sd", "/dev/hd", "/dev/xvd", "/dev/nvme"
    );

    /**
     * 返回命令的安全等级。
     */
    public Level classify(String command) {
        if (command == null || command.isBlank()) return Level.SAFE;

        String cmd = command.trim();
        for (String pattern : DANGEROUS_PATTERNS) {
            if (cmd.contains(pattern)) return Level.DANGEROUS;
        }

        String[] segments = cmd.split("[|&;]");
        Level maxLevel = Level.SAFE;
        for (String segment : segments) {
            Level segLevel = classifySimple(segment.trim());
            maxLevel = max(maxLevel, segLevel);
        }
        return segments.length > 1 && maxLevel == Level.SAFE ? Level.UNKNOWN : maxLevel;
    }

    private Level classifySimple(String cmd) {
        if (cmd.isEmpty()) return Level.SAFE;
        String[] parts = cmd.split("\\s+");
        String base = parts[0];
        int slashIdx = base.lastIndexOf('/');
        if (slashIdx >= 0) base = base.substring(slashIdx + 1);

        // 大小写不敏感匹配（兼容 Windows PowerShell）
        String lower = base.toLowerCase();

        if (DANGEROUS_CMDS.contains(lower)) return Level.DANGEROUS;
        if (DESTRUCTIVE_CMDS.contains(lower)) return Level.DESTRUCTIVE;

        if ("git".equals(lower) && parts.length > 1) {
            String sub = parts[1].toLowerCase();
            if (GIT_DESTRUCTIVE.contains(sub)) {
                String joined = String.join(" ", Arrays.copyOfRange(parts, 1, parts.length)).toLowerCase();
                if (joined.contains("--force") || joined.contains("-f")
                        || joined.contains("--hard") || joined.contains("--delete"))
                    return Level.DESTRUCTIVE;
                return Level.WRITE;
            }
            return Level.READ_ONLY;
        }

        if (WRITE_CMDS.contains(lower)) return Level.WRITE;
        if (READ_ONLY_CMDS.contains(lower)) return Level.READ_ONLY;
        if (SAFE_CMDS.contains(lower)) return Level.SAFE;

        if (("npm".equals(lower) || "yarn".equals(lower) || "pnpm".equals(lower))
                && parts.length > 1 && "run".equals(parts[1])) return Level.UNKNOWN;

        return Level.UNKNOWN;
    }

    /**
     * 根据安全等级返回权限决策。
     */
    public PermissionDecision checkPermission(String command) {
        Level level = classify(command);
        if (level.autoAllow()) return null;

        String cmdName = command.split("\\s+")[0];
        String message = switch (level) {
            case WRITE -> "Write command: " + cmdName;
            case DESTRUCTIVE -> "Destructive command: " + command.substring(0, Math.min(60, command.length()));
            case DANGEROUS -> "DANGEROUS command: " + command.substring(0, Math.min(60, command.length()));
            default -> "Unknown command: " + command.substring(0, Math.min(60, command.length()));
        };
        return new PermissionDecision.Ask(message, List.of("用更安全的替代方案"));
    }

    /**
     * 获取命令的 permissionContent（用于规则匹配）。
     */
    public static String permissionContent(String command) {
        if (command == null || command.isBlank()) return "";
        return command.trim().split("\\s+")[0];
    }

    private static Level max(Level a, Level b) {
        Level[] order = {Level.SAFE, Level.READ_ONLY, Level.WRITE, Level.DESTRUCTIVE, Level.DANGEROUS, Level.UNKNOWN};
        int aIdx = Arrays.asList(order).indexOf(a);
        int bIdx = Arrays.asList(order).indexOf(b);
        return order[Math.max(aIdx, bIdx)];
    }
}
