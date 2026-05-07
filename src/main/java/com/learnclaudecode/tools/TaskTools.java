package com.learnclaudecode.tools;

import com.learnclaudecode.tasks.TaskManager;
import com.learnclaudecode.tools.registry.AgentTool;
import com.learnclaudecode.tools.registry.AgentToolParam;

import java.util.List;

/**
 * 任务工具薄包装，委托给 tasks/TaskManager。
 *
 * @author BEAM
 */
public class TaskTools {

    private final TaskManager manager;

    public TaskTools(TaskManager manager) {
        this.manager = manager;
    }

    @AgentTool(name = "task_create", description = "Delegate work by posting a task on the shared board. Autonomous teammates auto-claim tasks when idle — you don't need spawn_teammate first. Provide a short subject and optional detailed description. Returns the task ID. Use task_list/task_update to monitor progress instead of polling read_inbox.", required = {"subject"})
    public String createTask(
            @AgentToolParam(description = "Short title for the task (required).") String subject,
            @AgentToolParam(description = "Detailed description including requirements and acceptance criteria.") String description) {
        return manager.create(subject, description);
    }

    @AgentTool(name = "task_get", description = "Get full details of a task by its numeric ID. Returns subject, description, status, owner, and dependency info.", required = {"task_id"})
    public String getTask(
            @AgentToolParam(description = "Numeric task ID (shown in task_list output).") int task_id) {
        return manager.get(task_id);
    }

    @AgentTool(name = "task_update", description = "Update a task on the board. Valid status values: pending, in_progress, completed. Use add_blocked_by (list of task IDs that must finish first) and add_blocks (list of task IDs that this task blocks) for dependency management.", required = {"task_id"})
    public String updateTask(
            @AgentToolParam(description = "Numeric task ID to update.") int task_id,
            @AgentToolParam(description = "New status: pending, in_progress, or completed.") String status,
            @AgentToolParam(description = "Task IDs that must finish before this task can start.") List<Integer> add_blocked_by,
            @AgentToolParam(description = "Task IDs that this task blocks (they depend on this one finishing).") List<Integer> add_blocks) {
        return manager.update(task_id, status, add_blocked_by, add_blocks);
    }

    @AgentTool(name = "task_list", description = "List all tasks on the shared board with ID, subject, status, and owner. Use to get an overview before assigning or updating work.")
    public String listTasks() {
        return manager.listAll();
    }

    @AgentTool(name = "claim_task", description = "Claim an unassigned task from the board by its numeric ID. Both lead and autonomous teammates can claim tasks. Use before starting work so others know the task is taken.", required = {"task_id"})
    public String claimTask(
            @AgentToolParam(description = "Numeric task ID to claim from the board.") int task_id) {
        return manager.claim(task_id, "lead");
    }
}
