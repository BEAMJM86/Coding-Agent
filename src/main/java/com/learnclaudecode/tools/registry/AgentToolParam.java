package com.learnclaudecode.tools.registry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 描述工具参数的元数据，写入 Anthropic API input_schema 的 property description。
 *
 * @author BEAM
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentToolParam {
    /** 参数描述，会出现在 tool schema 的 properties[paramName].description 中 */
    String description();

    /** 参数是否必填，默认 false。当为 true 时会追加到 method-level required 列表 */
    boolean required() default false;
}
