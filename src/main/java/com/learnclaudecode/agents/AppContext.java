package com.learnclaudecode.agents;

import com.learnclaudecode.background.BackgroundManager;
import com.learnclaudecode.common.AnthropicClient;
import com.learnclaudecode.common.EnvConfig;
import com.learnclaudecode.common.WorkspacePaths;
import com.learnclaudecode.context.CompressionService;
import com.learnclaudecode.permissions.*;
import com.learnclaudecode.tasks.TaskManager;
import com.learnclaudecode.tasks.WorktreeManager;
import com.learnclaudecode.team.MessageBus;
import com.learnclaudecode.team.TeammateManager;
import com.learnclaudecode.tools.*;
import com.learnclaudecode.tools.hooks.AuditHook;
import com.learnclaudecode.tools.hooks.OutputTruncator;
import com.learnclaudecode.tools.registry.ToolExecutor;
import com.learnclaudecode.tools.registry.ToolRegistry;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * 应用装配器，创建所有共享服务并装配成运行时。
 *
 * @author BEAM
 */
public final class AppContext {
    private final AgentRuntime runtime;

    public AppContext() {
        // 基础环境
        EnvConfig env = new EnvConfig();
        WorkspacePaths paths = new WorkspacePaths(env.getWorkdir());
        AnthropicClient client = new AnthropicClient(env);

        // 内部服务层（Manager）
        CommandTools commandTools = new CommandTools(paths);
        TodoManager todoManager = new TodoManager();
        SkillLoader skillLoader = new SkillLoader(paths);
        CompressionService compressionService = new CompressionService(paths, client, 50000, 3);
        TaskManager taskManager = new TaskManager(paths);
        BackgroundManager backgroundManager = new BackgroundManager(paths);
        MessageBus messageBus = new MessageBus(paths);

        // 安全层（权限系统）
        Path projectConfig = paths.workdir().resolve(".coding-agent").resolve("settings.json");
        Path globalConfig = Path.of(System.getProperty("user.home"), ".coding-agent", "settings.json");
        PermissionRules rules = new PermissionRules(projectConfig, globalConfig);
        TrustStore trustStore = new TrustStore(paths.workdir());
        trustStore.load();
        BashSafetyAnalyzer bashSafety = new BashSafetyAnalyzer();
        FileSafetyChecker fileSafety = new FileSafetyChecker();
        UserConfirmation userConfirm = new UserConfirmation(new Scanner(System.in), trustStore);
        PolicyEngine policyEngine = new PolicyEngine(rules, trustStore, bashSafety, fileSafety,
                userConfirm, PermissionMode.DEFAULT, false, null);

        // 工具注册
        ToolRegistry registry = new ToolRegistry();
        registry.scan(commandTools);      // bash, read_file, write_file, edit_file
        registry.scan(todoManager);       // todo
        registry.scan(skillLoader);       // load_skill
        registry.scan(new TaskTools(taskManager));
        registry.scan(new WorktreeTools(new WorktreeManager(paths, taskManager)));
        registry.scan(new BackgroundTools(backgroundManager));
        registry.scan(new GlobTool(paths.workdir()));        // Glob
        registry.scan(new GrepTool(paths.workdir()));        // Grep
        registry.scan(new AskUserQuestionTool(new Scanner(System.in))); // AskUserQuestion

        // hook 链 + PolicyEngine 固定层
        ToolExecutor toolExecutor = new ToolExecutor(registry,
                List.of(new AuditHook(), new OutputTruncator(50000)),
                Map.of(),
                policyEngine);

        TeammateManager teammateManager = new TeammateManager(paths, client, toolExecutor, messageBus, taskManager);
        TeammateTools teammateTools = new TeammateTools(teammateManager);
        registry.scan(teammateTools);     // spawn_teammate, list_teammates, shutdown_request, plan_approval
        registry.scan(new MessageBusTools(messageBus, teammateManager)); // send_message, read_inbox, broadcast

        // 运行时装配
        this.runtime = new AgentRuntime(client, paths, toolExecutor, registry,
                compressionService, todoManager, skillLoader,
                backgroundManager, messageBus, policyEngine);
        registry.scan(this.runtime);     // task (subagent)
    }

    public AgentRuntime runtime() {
        return runtime;
    }
}
