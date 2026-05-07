package com.learnclaudecode.tools;

import com.learnclaudecode.common.JsonUtils;
import com.learnclaudecode.team.MessageBus;
import com.learnclaudecode.team.TeammateManager;
import com.learnclaudecode.tools.registry.AgentTool;
import com.learnclaudecode.tools.registry.AgentToolParam;

import java.util.Map;

/**
 * 消息总线工具薄包装，委托给 team/MessageBus。
 *
 * @author BEAM
 */
public class MessageBusTools {

    private final MessageBus bus;
    private final TeammateManager teammateManager;

    public MessageBusTools(MessageBus bus, TeammateManager teammateManager) {
        this.bus = bus;
        this.teammateManager = teammateManager;
    }

    @AgentTool(name = "send_message", description = "Send a message to a named teammate. to: teammate name. content: message body. msg_type must be one of: message (general), broadcast (to all), shutdown_request, shutdown_response, plan_approval_response. Default is 'message'. Do NOT use claim_task or task_complete as msg_type.", required = {"to", "content"})
    public String sendMessage(
            @AgentToolParam(description = "Recipient teammate name. Must be 'lead' when sending from a teammate.") String to,
            @AgentToolParam(description = "Message body text.") String content,
            @AgentToolParam(description = "Message type. Valid: message (default), shutdown_request, shutdown_response, plan_approval_response. Do NOT use claim_task or task_complete.") String msg_type) {
        return bus.send("lead", to, content,
                msg_type != null ? msg_type : "message", Map.of());
    }

    @AgentTool(name = "read_inbox", description = "Read and drain all messages from the lead agent's inbox. Returns messages from teammates (status updates, plan requests, completion notices). Messages are consumed — each call clears the inbox.")
    public String readInbox() {
        return JsonUtils.toPrettyJson(bus.readInbox("lead"));
    }

    @AgentTool(name = "broadcast", description = "Send the same message to all currently active teammates simultaneously. Use for team-wide announcements, not for directing work to a specific teammate.", required = {"content"})
    public String broadcast(
            @AgentToolParam(description = "Message content to send to all teammates.") String content) {
        return bus.broadcast("lead", content, teammateManager.memberNames());
    }
}
