package com.learnclaudecode.tools.registry;

import com.learnclaudecode.agents.StageConfig;
import com.learnclaudecode.common.WorkspacePaths;
import com.learnclaudecode.model.ChatMessage;

import java.util.List;
import java.util.function.Consumer;

/**
 * 工具执行上下文，传入给 PolicyEngine、hook 和工具方法使用。
 *
 * @author BEAM
 */
public record ToolContext(
        WorkspacePaths paths,
        List<ChatMessage> messages,
        StageConfig config,
        Consumer<String> alternativeInjector
) {
    public ToolContext(WorkspacePaths paths, List<ChatMessage> messages, StageConfig config) {
        this(paths, messages, config, null);
    }
}
