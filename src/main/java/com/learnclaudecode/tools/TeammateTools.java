package com.learnclaudecode.tools;

import com.learnclaudecode.team.TeammateManager;
import com.learnclaudecode.tools.registry.AgentTool;
import com.learnclaudecode.tools.registry.AgentToolParam;

/**
 * 队友工具薄包装，委托给 team/TeammateManager。
 *
 * @author BEAM
 */
public class TeammateTools {

    private final TeammateManager manager;

    public TeammateTools(TeammateManager manager) {
        this.manager = manager;
    }

    @AgentTool(name = "spawn_teammate", description = "Create and start a persistent teammate agent. Use ONLY for teammates needing custom behavior that task_create can't express. For standard delegation, use task_create instead — autonomous teammates auto-claim tasks from the board without needing spawn_teammate. The teammate communicates via send_message / read_inbox.", required = {"name", "role", "prompt"})
    public String spawnTeammate(
            @AgentToolParam(description = "Unique name for the teammate (e.g. 'bubble-sorter').") String name,
            @AgentToolParam(description = "What the teammate does (e.g. 'Python developer responsible for bubble sort').") String role,
            @AgentToolParam(description = "Initial task instructions including what to do and how to report back to lead.") String prompt,
            @AgentToolParam(description = "Whether the teammate can auto-claim tasks from the board when idle. Default false.") boolean autonomous) {
        return manager.spawn(name, role, prompt, autonomous);
    }

    @AgentTool(name = "list_teammates", description = "Show all teammates with name, role, and status (working/idle/shutdown). Use to check who is available before assigning work or to monitor progress.")
    public String listTeammates() {
        return manager.listAll();
    }

    @AgentTool(name = "shutdown_request", description = "Send a shutdown signal to a teammate by name. The teammate may accept or reject. Use after a teammate has completed their task and is no longer needed.")
    public String requestShutdown(
            @AgentToolParam(description = "Name of the teammate to shut down.") String teammate) {
        return manager.handleShutdownRequest(teammate);
    }

    @AgentTool(name = "plan_approval", description = "Respond to a teammate's plan approval request. request_id comes from the teammate's plan_approval message in your inbox. Set approve=true to authorize or false to reject, with optional feedback explaining why.")
    public String approvePlan(
            @AgentToolParam(description = "The request ID from the teammate's plan_approval message (found in inbox).") String request_id,
            @AgentToolParam(description = "true to authorize the plan, false to reject.") boolean approve,
            @AgentToolParam(description = "Explanation for the decision, especially if rejecting.") String feedback) {
        return manager.handlePlanReview(request_id, approve, feedback);
    }
}
