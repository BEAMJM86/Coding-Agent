package com.learnclaudecode.tools;

import com.learnclaudecode.background.BackgroundManager;
import com.learnclaudecode.tools.registry.AgentTool;
import com.learnclaudecode.tools.registry.AgentToolParam;

/**
 * 后台任务工具薄包装，委托给 background/BackgroundManager。
 *
 * @author BEAM
 */
public class BackgroundTools {

    private final BackgroundManager manager;

    public BackgroundTools(BackgroundManager manager) {
        this.manager = manager;
    }

    @AgentTool(name = "background_run", description = "Run a shell command asynchronously in a background thread. Returns a task_id immediately. Use check_background(task_id) to poll for results. Optional timeout in seconds (default 120). Use for long-running tasks that shouldn't block the main agent loop.")
    public String runBackground(
            @AgentToolParam(description = "The shell command to run in background.") String command,
            @AgentToolParam(description = "Maximum seconds before force-killing the command. Default 120.") Integer timeout) {
        return manager.run(command, timeout != null ? timeout : 120);
    }

    @AgentTool(name = "check_background", description = "Check a background task started by background_run. Only tracks background_run tasks — does NOT track teammates. Use list_teammates or read_inbox for teammate status.")
    public String checkBackground(
            @AgentToolParam(description = "The task ID returned by background_run.") String task_id) {
        return manager.check(task_id != null ? task_id : "");
    }
}
