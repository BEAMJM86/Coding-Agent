package com.learnclaudecode.tools;

import com.learnclaudecode.common.WorkspacePaths;
import com.learnclaudecode.tools.registry.AgentTool;
import com.learnclaudecode.tools.registry.AgentToolParam;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基础文件与命令工具，提供 bash、read_file、write_file、edit_file 四个核心能力。
 *
 * @author BEAM
 */
public class CommandTools {
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");
    private final WorkspacePaths paths;

    public CommandTools(WorkspacePaths paths) {
        this.paths = paths;
    }

    @AgentTool(description = "Execute a shell command in the workspace. Returns combined stdout+stderr. 120s timeout. Use for build, test, file ops (ls/cp/find), git, and running scripts.", required = {"command"})
    public String bash(@AgentToolParam(description = "The shell command to execute. Use Unix-style syntax with forward slashes.") String command) {
        // 硬编码危险模式防绕过（PolicyEngine 兜底层）
        if (command != null) {
            String cmd = command.toLowerCase().replaceAll("\\s+", " ").trim();
            java.util.List<String> forbidden = java.util.List.of(
                "sudo ", "shutdown", "reboot", "mkfs ", "dd if=",
                "rm -rf /", "rm -rf --no-preserve-root",
                ":(){ :|:& };:", "chmod 777 /"
            );
            for (String f : forbidden) {
                if (cmd.contains(f)) {
                    return "Error: Dangerous command blocked by hardcoded security policy: " + f;
                }
            }
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(shellCommand(command));
            builder.directory(paths.workdir().toFile());
            builder.redirectErrorStream(true);
            Process process = builder.start();
            // 在独立线程中读取输出，防止管道缓冲区满导致进程死锁
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            Thread reader = new Thread(() -> {
                try (InputStream in = process.getInputStream()) {
                    in.transferTo(buffer);
                } catch (IOException ignored) {
                }
            }, "bash-reader");
            reader.start();
            boolean finished = process.waitFor(120, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.interrupt();
                return "Error: Timeout (120s)";
            }
            reader.join(5000);
            String out = buffer.toString(StandardCharsets.UTF_8).trim();
            if (out.isBlank()) {
                return "(no output)";
            }
            return out.substring(0, Math.min(50000, out.length()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Error: interrupted";
        } catch (IOException e) {
            return "Error: " + e.getMessage();
        }
    }

    public List<String> shellCommand(String command) {
        if (IS_WINDOWS) {
            return List.of("powershell", "-Command", command);
        }
        return List.of("bash", "-lc", command);
    }

    @AgentTool(name = "read_file", description = "Read a file from the workspace. Returns numbered lines. Use optional limit=N to read only first N lines. Path is relative to workspace root.", required = {"path"})
    public String readFile(
            @AgentToolParam(description = "Path to the file relative to workspace root.") String path,
            @AgentToolParam(description = "Maximum number of lines to read. If omitted, reads the entire file.") Integer limit) {
        try {
            List<String> allLines = Files.readAllLines(paths.safeResolve(path), StandardCharsets.UTF_8);
            List<String> lines = new ArrayList<>(allLines);
            if (limit != null && limit > 0 && limit < allLines.size()) {
                lines = new ArrayList<>(allLines.subList(0, limit));
                lines.add("... (" + (allLines.size() - limit) + " more lines)");
            }
            String text = String.join("\n", lines);
            return text.substring(0, Math.min(50000, text.length()));
        } catch (java.nio.file.NoSuchFileException e) {
            return "Error: File not found: " + path;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @AgentTool(name = "write_file", description = "Create or overwrite a file with new content. Creates parent directories if needed. Path is relative to workspace root. Use for new files or complete rewrites.", required = {"path", "content"})
    public String writeFile(
            @AgentToolParam(description = "Path to the file relative to workspace root. Parent directories created automatically.") String path,
            @AgentToolParam(description = "The complete file content to write.") String content) {
        try {
            Path target = paths.safeResolve(path);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.writeString(target, content, StandardCharsets.UTF_8);
            return "Wrote " + content.length() + " bytes to " + path;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    @AgentTool(name = "edit_file", description = "Find and replace a single occurrence of old_text with new_text in a file. old_text must match exactly (including whitespace). Only the first match is replaced. Use for targeted edits without rewriting the whole file.", required = {"path", "old_text", "new_text"})
    public String editFile(
            @AgentToolParam(description = "Path to the file relative to workspace root.") String path,
            @AgentToolParam(description = "The exact text to find and replace. Must match character-for-character including whitespace.") String old_text,
            @AgentToolParam(description = "The replacement text to write in place of old_text.") String new_text) {
        try {
            Path target = paths.safeResolve(path);
            String content = Files.readString(target, StandardCharsets.UTF_8);
            if (!content.contains(old_text)) {
                return "Error: Text not found in " + path;
            }
            Files.writeString(target,
                    content.replaceFirst(java.util.regex.Pattern.quote(old_text),
                            java.util.regex.Matcher.quoteReplacement(new_text)),
                    StandardCharsets.UTF_8);
            return "Edited " + path;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
