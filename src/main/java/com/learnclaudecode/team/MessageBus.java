package com.learnclaudecode.team;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.learnclaudecode.common.JsonUtils;
import com.learnclaudecode.common.WorkspacePaths;
import lombok.extern.slf4j.Slf4j;

/**
 * 文件型消息总线，对齐 .team/inbox/*.jsonl 协议。
 *
 * @author BEAM
 */
@Slf4j
public class MessageBus {
    public static final List<String> VALID_TYPES = List.of(
            "message",
            "broadcast",
            "shutdown_request",
            "shutdown_response",
            "plan_approval_response"
    );

    private final Path inboxDir;

    /**
     * 初始化文件型消息总线。
     *
     * @param paths 工作区路径工具
     */
    public MessageBus(WorkspacePaths paths) {
        this.inboxDir = paths.inboxDir();
        try {
            Files.createDirectories(inboxDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建 inbox 目录", e);
        }
    }

    /**
     * 向指定收件箱发送一条消息。
     *
     * @param sender 发送者
     * @param to 接收者
     * @param content 消息内容
     * @param msgType 消息类型
     * @param extra 扩展字段
     * @return 发送结果
     */
    public synchronized String send(String sender, String to, String content, String msgType, Map<String, Object> extra) {
        if (!VALID_TYPES.contains(msgType)) {
            log.warn("无效的消息类型: {}", msgType);
            return "Error: Invalid type '" + msgType + "'";
        }
        // 每条消息都落成一行 JSON，便于多代理通过文件系统进行最小依赖的通信。
        Map<String, Object> message = new HashMap<>();
        message.put("type", msgType);
        message.put("from", sender);
        message.put("content", content);
        message.put("timestamp", Instant.now().toEpochMilli() / 1000.0);
        if (extra != null) {
            message.putAll(extra);
        }
        Path path = inboxDir.resolve(to + ".jsonl");
        log.info("{} -> {} [{}]: {}", sender, to, msgType, content.substring(0, Math.min(100, content.length())));
        try {
            Files.writeString(path, JsonUtils.toJson(message) + System.lineSeparator(), StandardCharsets.UTF_8,
                    Files.exists(path) ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
            log.trace("消息已写入文件: {}", path);
        } catch (IOException e) {
            log.error("发送消息失败: {}", e.getMessage(), e);
            return "Error: " + e.getMessage();
        }
        return "Sent " + msgType + " to " + to;
    }

    /**
     * 读取并清空指定收件箱。
     *
     * @param name 收件箱名称
     * @return 收到的消息列表
     */
    public synchronized List<Map<String, Object>> readInbox(String name) {
        Path path = inboxDir.resolve(name + ".jsonl");
        if (!Files.exists(path)) {
            log.debug("[inbox] {} 的收件箱为空（文件不存在）", name);
            return List.of();
        }
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            // 读取后立即清空，实现“读即消费”的 inbox 语义。
            Files.writeString(path, "", StandardCharsets.UTF_8);
            List<Map<String, Object>> messages = new ArrayList<>();
            for (String line : lines) {
                if (!line.isBlank()) {
                    messages.add(JsonUtils.fromJson(line, Map.class));
                }
            }
            if (messages.isEmpty()) {
                log.debug("[inbox] {} 的收件箱为空（0条消息）", name);
            } else {
                // 汇总每条消息的发送者和类型，便于追踪多代理通信
                StringBuilder summary = new StringBuilder();
                for (int i = 0; i < messages.size(); i++) {
                    Map<String, Object> msg = messages.get(i);
                    String from = String.valueOf(msg.getOrDefault("from", "?"));
                    String type = String.valueOf(msg.getOrDefault("type", "message"));
                    String preview = String.valueOf(msg.getOrDefault("content", ""));
                    preview = preview.substring(0, Math.min(60, preview.length())).replace("\n", " ");
                    if (i > 0) summary.append("; ");
                    summary.append(String.format("[%s] %s->\"%s\"", type, from, preview));
                }
                log.info("[inbox] {} 收到 {} 条消息 ← {}", name, messages.size(), summary);
            }
            return messages;
        } catch (IOException e) {
            log.error("读取收件箱 {} 失败: {}", name, e.getMessage(), e);
            return List.of(Map.of("type", "message", "from", "system", "content", "Error: " + e.getMessage()));
        }
    }

    /**
     * 向多个队友广播消息。
     *
     * @param sender 发送者
     * @param content 广播内容
     * @param names 广播目标列表
     * @return 广播结果
     */
    public String broadcast(String sender, String content, List<String> names) {
        log.debug("广播消息从 {} 到 {} 个队友", sender, names.size());
        int count = 0;
        for (String name : names) {
            if (!name.equals(sender)) {
                send(sender, name, content, "broadcast", Map.of());
                count++;
            }
        }
        log.info("广播完成，发送给 {} 个队友", count);
        return "Broadcast to " + count + " teammates";
    }
}
