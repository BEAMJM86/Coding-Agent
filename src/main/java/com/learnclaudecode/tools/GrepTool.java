package com.learnclaudecode.tools;

import com.learnclaudecode.tools.registry.AgentTool;
import com.learnclaudecode.tools.registry.AgentToolParam;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 纯 Java Grep 内容搜索工具，基于 NIO.2 + java.util.regex。
 * 替代 bash grep/rg 命令，避免权限确认和跨平台兼容问题。
 *
 * @author BEAM
 */
public class GrepTool {

    private final Path workingDirectory;

    private static final Map<String, String[]> FILE_TYPE_EXTENSIONS = new LinkedHashMap<>();
    static {
        FILE_TYPE_EXTENSIONS.put("java", new String[]{"*.java"});
        FILE_TYPE_EXTENSIONS.put("js", new String[]{"*.js", "*.jsx"});
        FILE_TYPE_EXTENSIONS.put("ts", new String[]{"*.ts", "*.tsx"});
        FILE_TYPE_EXTENSIONS.put("py", new String[]{"*.py"});
        FILE_TYPE_EXTENSIONS.put("rust", new String[]{"*.rs"});
        FILE_TYPE_EXTENSIONS.put("go", new String[]{"*.go"});
        FILE_TYPE_EXTENSIONS.put("xml", new String[]{"*.xml"});
        FILE_TYPE_EXTENSIONS.put("json", new String[]{"*.json"});
        FILE_TYPE_EXTENSIONS.put("yaml", new String[]{"*.yaml", "*.yml"});
        FILE_TYPE_EXTENSIONS.put("md", new String[]{"*.md", "*.markdown"});
        FILE_TYPE_EXTENSIONS.put("sh", new String[]{"*.sh", "*.bash"});
    }

    public GrepTool(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @AgentTool(name = "Grep", description = """
            A powerful search tool built on Java regex.
            Usage:
            - ALWAYS use Grep for search tasks. NEVER invoke grep or rg as a Bash command.
            - Supports full regex syntax (e.g., "log.*Error", "function\\s+\\w+")
            - Filter files with glob parameter (e.g., "*.js", "**/*.tsx") or type parameter (e.g., "js", "py", "rust")
            - Output modes: "content" shows matching lines, "files_with_matches" shows only file paths (default), "count" shows match counts
            - Use Agent tool for open-ended searches requiring multiple rounds
            - Multiline matching: By default patterns match within single lines only.""")
    public String grep(
            @AgentToolParam(description = "The regular expression pattern to search for in file contents") String pattern,
            @AgentToolParam(description = "File or directory to search in. Defaults to current working directory.") String path,
            @AgentToolParam(description = "Glob pattern to filter files (e.g. \"*.js\", \"**/*.tsx\")") String glob,
            @AgentToolParam(description = "Output mode: \"content\" shows matching lines, \"files_with_matches\" shows file paths (default), \"count\" shows match counts.") String outputMode,
            @AgentToolParam(description = "Number of lines to show before and after each match. Requires output_mode: \"content\".") Integer context,
            @AgentToolParam(description = "Show line numbers in output. Defaults to true for content mode.") Boolean showLineNumbers,
            @AgentToolParam(description = "Case insensitive search") Boolean caseInsensitive,
            @AgentToolParam(description = "File type to search. Common types: js, py, rust, go, java, etc.") String type,
            @AgentToolParam(description = "Limit output to first N lines/entries.") Integer headLimit) {

        if (pattern == null || pattern.isBlank()) {
            return "Error: pattern must not be empty";
        }

        try {
            Path searchPath;
            if (path != null && !path.isBlank()) {
                searchPath = Path.of(path);
            } else {
                searchPath = workingDirectory;
            }

            if (!Files.exists(searchPath)) {
                return "Error: Path does not exist: " + searchPath.toAbsolutePath();
            }

            int flags = Pattern.MULTILINE;
            if (Boolean.TRUE.equals(caseInsensitive)) {
                flags |= Pattern.CASE_INSENSITIVE;
            }

            Pattern searchPattern;
            try {
                searchPattern = Pattern.compile(pattern, flags);
            } catch (Exception e) {
                return "Error: Invalid regex pattern: " + e.getMessage();
            }

            String mode = (outputMode != null && !outputMode.isBlank()) ? outputMode : "files_with_matches";
            List<PathMatcher> globMatchers = buildGlobMatchers(glob, type);

            return switch (mode) {
                case "content" -> searchContent(searchPath, searchPattern, globMatchers,
                        context != null ? context : 0, Boolean.TRUE.equals(showLineNumbers), headLimit);
                case "count" -> searchCount(searchPath, searchPattern, globMatchers, headLimit);
                default -> searchFilesWithMatches(searchPath, searchPattern, globMatchers, headLimit);
            };

        } catch (Exception e) {
            return "Error executing grep: " + e.getMessage();
        }
    }

    private List<PathMatcher> buildGlobMatchers(String glob, String type) {
        List<PathMatcher> matchers = new ArrayList<>();
        if (type != null && !type.isBlank()) {
            String[] extensions = FILE_TYPE_EXTENSIONS.get(type.toLowerCase());
            if (extensions != null) {
                for (String ext : extensions) {
                    matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + ext));
                }
            }
        }
        if (glob != null && !glob.isBlank()) {
            String globPattern = glob.startsWith("**/") ? glob : "**/" + glob;
            matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + globPattern));
        }
        return matchers;
    }

    private boolean matchesGlob(Path file, List<PathMatcher> matchers) {
        if (matchers.isEmpty()) return true;
        for (PathMatcher m : matchers) {
            if (m.matches(file)) return true;
        }
        return false;
    }

    private boolean isIgnored(Path p) {
        String s = p.toString().replace('\\', '/');
        return s.contains("/.git/") || s.contains("/node_modules/") || s.contains("/target/")
                || s.contains("/build/") || s.contains("/.idea/") || s.contains("/.vscode/")
                || s.contains("/dist/") || s.contains("/__pycache__/");
    }

    private String searchFilesWithMatches(Path searchPath, Pattern pattern, List<PathMatcher> matchers,
                                          Integer headLimit) throws IOException {
        List<String> matchingFiles = new ArrayList<>();
        processFiles(searchPath, matchers, file -> {
            if (headLimit != null && headLimit > 0 && matchingFiles.size() >= headLimit) return false;
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.length() > 10000) continue;
                    if (pattern.matcher(line).find()) {
                        matchingFiles.add(file.toString());
                        break;
                    }
                }
            } catch (IOException ignored) {}
            return true;
        });

        if (matchingFiles.isEmpty()) {
            return "No matches found for pattern: " + pattern.pattern();
        }
        return String.join("\n", matchingFiles);
    }

    private String searchCount(Path searchPath, Pattern pattern, List<PathMatcher> matchers,
                               Integer headLimit) throws IOException {
        Map<String, Integer> fileCounts = new LinkedHashMap<>();
        processFiles(searchPath, matchers, file -> {
            if (headLimit != null && headLimit > 0 && fileCounts.size() >= headLimit) return false;
            int count = 0;
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.length() > 10000) continue;
                    if (pattern.matcher(line).find()) count++;
                }
            } catch (IOException ignored) {}
            if (count > 0) fileCounts.put(file.toString(), count);
            return true;
        });

        if (fileCounts.isEmpty()) {
            return "No matches found for pattern: " + pattern.pattern();
        }
        StringBuilder sb = new StringBuilder();
        for (var entry : fileCounts.entrySet()) {
            sb.append(entry.getKey()).append(":").append(entry.getValue()).append("\n");
        }
        return sb.toString().trim();
    }

    private String searchContent(Path searchPath, Pattern pattern, List<PathMatcher> matchers,
                                 int context, boolean lineNumbers, Integer headLimit) throws IOException {
        StringBuilder result = new StringBuilder();
        AtomicInteger lineCount = new AtomicInteger(0);

        processFiles(searchPath, matchers, file -> {
            if (headLimit != null && headLimit > 0 && lineCount.get() >= headLimit) return false;
            try {
                List<String> allLines = Files.readAllLines(file, StandardCharsets.UTF_8);
                boolean fileHeaderAdded = false;
                for (int i = 0; i < allLines.size(); i++) {
                    if (headLimit != null && headLimit > 0 && lineCount.get() >= headLimit) break;
                    String line = allLines.get(i);
                    if (line.length() > 10000) continue;
                    if (pattern.matcher(line).find()) {
                        if (!fileHeaderAdded) {
                            result.append(file.toString()).append("\n");
                            fileHeaderAdded = true;
                        }
                        int start = Math.max(0, i - context);
                        int end = Math.min(allLines.size() - 1, i + context);
                        for (int j = start; j <= end; j++) {
                            String prefix = lineNumbers ? (j + 1) + (j == i ? ":  " : ":- ") : (j == i ? "  " : "- ");
                            result.append(prefix).append(allLines.get(j)).append("\n");
                            lineCount.incrementAndGet();
                        }
                        result.append("--\n");
                        lineCount.incrementAndGet();
                    }
                }
            } catch (IOException ignored) {}
            return true;
        });

        if (result.isEmpty()) {
            return "No matches found for pattern: " + pattern.pattern();
        }
        return result.toString().trim();
    }

    private void processFiles(Path searchPath, List<PathMatcher> matchers, FileProcessor processor) throws IOException {
        if (Files.isRegularFile(searchPath)) {
            if (matchesGlob(searchPath, matchers)) processor.process(searchPath);
        } else if (Files.isDirectory(searchPath)) {
            try (Stream<Path> paths = Files.walk(searchPath, 100, FileVisitOption.FOLLOW_LINKS)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> matchesGlob(p, matchers))
                        .filter(p -> !isIgnored(p))
                        .anyMatch(file -> !processor.process(file));
            }
        }
    }

    @FunctionalInterface
    private interface FileProcessor {
        boolean process(Path file);
    }
}
