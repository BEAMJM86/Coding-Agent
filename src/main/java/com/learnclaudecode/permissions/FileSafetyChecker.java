package com.learnclaudecode.permissions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文件安全检查：敏感路径保护 + 写前读 + 指纹校验。
 *
 * @author BEAM
 */
public class FileSafetyChecker {

    private static final Set<String> PROTECTED_FILES = Set.of(
            ".env", ".npmrc", ".pypirc", ".netrc",
            ".aws/credentials", ".ssh/config", ".kube/config",
            ".gitconfig", "Makefile", "Dockerfile"
    );

    private static final Set<String> PROTECTED_DIRS = Set.of(
            ".git", ".ssh", ".gnupg", ".config", ".vscode", ".idea"
    );

    private static final Set<String> PROTECTED_LOCKFILES = Set.of(
            "package-lock.json", "yarn.lock", "pnpm-lock.yaml",
            "poetry.lock", "Cargo.lock", "go.sum"
    );

    private final Map<String, String> readFingerprints = new ConcurrentHashMap<>();

    /**
     * 记录文件已完整读取，保存指纹用于写入前校验。
     */
    public void recordRead(String relativePath, String content) {
        String hash = hash(content);
        readFingerprints.put(relativePath, hash);
    }

    /**
     * 检查写入/编辑前的文件状态。
     */
    public PermissionDecision checkBeforeWrite(String relativePath, String currentContent) {
        String filename = Path.of(relativePath).getFileName().toString();
        for (String dir : PROTECTED_DIRS) {
            if (relativePath.startsWith(dir + "/") || relativePath.startsWith(dir + "\\")) {
                return new PermissionDecision.Ask(
                        "Writing to protected directory: " + relativePath,
                        List.of("确认写入路径"));
            }
        }
        if (PROTECTED_FILES.contains(filename)) {
            return new PermissionDecision.Ask(
                    "Writing to protected file: " + filename,
                    List.of("确认修改此文件"));
        }
        if (PROTECTED_LOCKFILES.contains(filename)) {
            return new PermissionDecision.Ask(
                    "Writing to lockfile: " + filename,
                    List.of("确认修改 lockfile"));
        }

        String existingHash = readFingerprints.get(relativePath);
        if (existingHash == null && currentContent != null && !currentContent.isEmpty()) {
            return new PermissionDecision.Deny(
                    "File must be read before writing: " + relativePath,
                    DecisionReason.safety("write-before-read"));
        }

        if (existingHash != null && currentContent != null) {
            String currentHash = hash(currentContent);
            if (!existingHash.equals(currentHash)) {
                return new PermissionDecision.Deny(
                        "File changed since last read: " + relativePath,
                        DecisionReason.safety("stale-read"));
            }
        }

        if ((relativePath.endsWith(".md") || relativePath.endsWith(".markdown"))
                && currentContent == null) {
            return new PermissionDecision.Ask(
                    "Allow creating .md file: " + filename + "?",
                    List.of("确认创建文档"));
        }

        return null;
    }

    /**
     * 获取路径的 permissionContent（用于规则匹配）。
     */
    public static String permissionContent(String filePath) {
        if (filePath == null || filePath.isBlank()) return "";
        return filePath;
    }

    private String hash(String content) {
        if (content == null) return "";
        try {
            var md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return String.valueOf(content.hashCode());
        }
    }
}
