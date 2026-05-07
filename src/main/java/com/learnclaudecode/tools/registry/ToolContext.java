package com.learnclaudecode.tools.registry;

import com.learnclaudecode.agents.StageConfig;
import com.learnclaudecode.common.WorkspacePaths;
import com.learnclaudecode.model.ChatMessage;

import java.util.List;

/**
 * 工具执行上下文，传入给 hook 和工具方法使用。
 *
 * @author BEAM
 */
public record ToolContext(
        WorkspacePaths paths,
        List<ChatMessage> messages,
        StageConfig config
) {}
