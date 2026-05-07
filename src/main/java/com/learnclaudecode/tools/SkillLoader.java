package com.learnclaudecode.tools;

import com.learnclaudecode.common.WorkspacePaths;
import com.learnclaudecode.tools.registry.AgentTool;
import com.learnclaudecode.tools.registry.AgentToolParam;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 技能加载器，扫描 skills 目录下的 SKILL.md 文件并解析简单 frontmatter。
 *
 * @author BEAM
 */
public class SkillLoader {
    private final Map<String, Map<String, Object>> skills = new HashMap<>();

    public SkillLoader(WorkspacePaths paths) {
        Path skillsDir = paths.skillsDir();
        if (!Files.exists(skillsDir)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(skillsDir)) {
            stream.filter(p -> p.getFileName().toString().equals("SKILL.md"))
                    .sorted()
                    .forEach(this::loadSkillFile);
        } catch (IOException ignored) {
        }
    }

    private void loadSkillFile(Path path) {
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            Map<String, String> meta = new HashMap<>();
            String body = text;
            if (text.startsWith("---\n")) {
                int second = text.indexOf("\n---\n", 4);
                if (second > 0) {
                    String header = text.substring(4, second);
                    body = text.substring(second + 5).trim();
                    for (String line : header.split("\n")) {
                        int idx = line.indexOf(':');
                        if (idx > 0) {
                            meta.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
                        }
                    }
                }
            }
            String name = meta.getOrDefault("name", path.getParent().getFileName().toString());
            skills.put(name, Map.of(
                    "meta", meta,
                    "body", body,
                    "path", path.toString()
            ));
        } catch (IOException ignored) {
        }
    }

    public String getDescriptions() {
        if (skills.isEmpty()) {
            return "(no skills available)";
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : skills.entrySet()) {
            @SuppressWarnings("unchecked")
            Map<String, String> meta = (Map<String, String>) entry.getValue().get("meta");
            lines.add("  - " + entry.getKey() + ": " + meta.getOrDefault("description", "No description"));
        }
        return String.join("\n", lines);
    }

    @AgentTool(name = "load_skill", description = "Load specialized knowledge and instructions for a named skill. Returns the full SKILL.md content. Use when the task requires domain-specific expertise (e.g. PDF processing, image analysis). Call list_skills first if unsure what skills are available.", required = {"name"})
    public String getContent(
            @AgentToolParam(description = "Name of the skill to load (use list_skills to see available skills).") String name) {
        Map<String, Object> skill = skills.get(name);
        if (skill == null) {
            return "Error: Unknown skill '" + name + "'. Available: " + String.join(", ", skills.keySet());
        }
        return "<skill name=\"" + name + "\">\n" + skill.get("body") + "\n</skill>";
    }
}
