package com.learnclaudecode.tools.hooks;

/**
 * 链式调用接口，调用 proceed() 执行后续 hook 链及工具本身。
 *
 * @author BEAM
 */
@FunctionalInterface
public interface Proceed {
    String proceed();
}
