package com.learnclaudecode.agents;

/**
 * 统一入口启动器。
 *
 * 负责创建应用上下文并启动 Agent 运行时。
 *
 * @author BEAM
 */
public final class Launcher {
    /**
     * 禁止外部实例化工具型启动器。
     */
    private Launcher() {
    }

    /**
     * 使用指定配置启动交互式 Agent 运行时。
     *
     * @param config 能力配置
     */
    public static void launch(StageConfig config) {
        // 创建完整应用上下文，再把配置交给统一运行时执行。
        new AppContext().runtime().runRepl(config);
    }
}
