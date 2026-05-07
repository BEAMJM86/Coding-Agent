package com.learnclaudecode.tools;

import com.learnclaudecode.tasks.WorktreeManager;
import com.learnclaudecode.tools.registry.AgentTool;
import com.learnclaudecode.tools.registry.AgentToolParam;

/**
 * Worktree 工具薄包装，委托给 tasks/WorktreeManager。
 *
 * @author BEAM
 */
public class WorktreeTools {

    private final WorktreeManager manager;

    public WorktreeTools(WorktreeManager manager) {
        this.manager = manager;
    }

    @AgentTool(name = "worktree_create", description = "Create an isolated git worktree for a specific task. name: worktree identifier. task_id: associated task number. Worktrees give each teammate a separate working copy so they don't collide on file changes.", required = {"name", "task_id"})
    public String createWorktree(
            @AgentToolParam(description = "Unique worktree identifier.") String name,
            @AgentToolParam(description = "Numeric task ID to associate with this worktree.") int task_id) {
        return manager.create(name, task_id);
    }

    @AgentTool(name = "worktree_list", description = "Show all worktrees with name and associated task ID. Use to see which tasks have isolated workspaces.")
    public String listWorktrees() {
        return manager.list();
    }

    @AgentTool(name = "worktree_remove", description = "Delete a worktree by name. Set keep=true to preserve the working directory on disk. Set keep=false for a full cleanup after task completion.", required = {"name"})
    public String removeWorktree(
            @AgentToolParam(description = "Name of the worktree to remove.") String name,
            @AgentToolParam(description = "true = preserve files on disk; false = full deletion.") boolean keep) {
        return manager.remove(name, keep);
    }

    @AgentTool(name = "worktree_events", description = "Show recent worktree lifecycle events (created, removed) for auditing. Optional limit=N controls how many events to return (default 20).")
    public String worktreeEvents(
            @AgentToolParam(description = "Max number of events to return. Default 20.") Integer limit) {
        return manager.recentEvents(limit != null ? limit : 20);
    }
}
