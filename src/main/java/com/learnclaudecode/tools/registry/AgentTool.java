package com.learnclaudecode.tools.registry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个方法为 Agent 可调用的工具。
 * 对应 Spring AI 的 @Tool 注解，零框架依赖版本。
 *
 * @author BEAM
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentTool {
    /** 工具名，默认用方法名 */
    String name() default "";

    /** 工具描述 */
    String description();

    /** 必填参数名列表 */
    String[] required() default {};
}
