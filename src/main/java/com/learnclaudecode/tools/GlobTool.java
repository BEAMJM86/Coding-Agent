package com.learnclaudecode.tools;

import com.learnclaudecode.tools.registry.AgentTool;
import com.learnclaudecode.tools.registry.AgentToolParam;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 纯 Java Glob 文件模式匹配工具，基于 NIO.2 实现。
 * 用于快速查找文件，避免使用 bash ls/find 带来的权限确认和性能问题。
 *
 * @author BEAM
 */
public class GlobTool {

    private final Path workingDirectory;

    public GlobTool(Path workingDirectory) {
        this.workingDirectory = workingDirectory;
    }

    @AgentTool(name = "Glob", description = """
            Fast file pattern matching tool that works with any codebase size.
            Supports glob patterns like "**/*.js" or "src/**/*.ts".
            Returns matching file paths sorted by modification time.
            Use this tool when you need to find files by name patterns.
            When you are doing an open ended search that may require multiple rounds of globbing and grepping, use the Agent tool instead.
            You can call multiple tools in a single response. It is always better to speculatively perform multiple searches in parallel if they are potentially useful.""")
    public String glob(
            @AgentToolParam(description = "The glob pattern to match files against") String pattern,
            @AgentToolParam(description = "The directory to search in. If not specified, the current working directory will be used. IMPORTANT: Omit this field to use the default directory. DO NOT enter \"undefined\" or \"null\" - simply omit it for the default behavior. Must be a valid directory path if provided.") String path) {

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
            if (!Files.isDirectory(searchPath)) {
                return "Error: Path is not a directory: " + searchPath.toAbsolutePath();
            }

            String globPattern = pattern.startsWith("**/") ? pattern : "**/" + pattern;
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + globPattern);

            List<FileInfo> matchingFiles = new ArrayList<>();
            try (Stream<Path> paths = Files.walk(searchPath, 100, FileVisitOption.FOLLOW_LINKS)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> !isIgnored(p))
                        .filter(p -> matches(p, searchPath, matcher))
                        .limit(1000)
                        .forEach(file -> {
                            try {
                                BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
                                matchingFiles.add(new FileInfo(file, attrs.lastModifiedTime().toMillis()));
                            } catch (IOException e) {
                                matchingFiles.add(new FileInfo(file, 0));
                            }
                        });
            }

            if (matchingFiles.isEmpty()) {
                return "No files found matching pattern: " + pattern;
            }

            matchingFiles.sort(Comparator.comparingLong(FileInfo::modificationTime).reversed());

            StringBuilder result = new StringBuilder();
            for (FileInfo info : matchingFiles) {
                result.append(info.path().toString()).append("\n");
            }
            return result.toString().trim();

        } catch (Exception e) {
            return "Error executing glob: " + e.getMessage();
        }
    }

    private boolean matches(Path file, Path searchPath, PathMatcher matcher) {
        if (matcher.matches(file)) return true;
        try {
            return matcher.matches(searchPath.relativize(file));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean isIgnored(Path p) {
        String s = p.toString().replace('\\', '/');
        return s.contains("/.git/") || s.contains("/node_modules/") || s.contains("/target/")
                || s.contains("/build/") || s.contains("/.idea/") || s.contains("/.vscode/")
                || s.contains("/dist/") || s.contains("/__pycache__/");
    }

    private record FileInfo(Path path, long modificationTime) {}
}
