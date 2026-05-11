package com.learnclaudecode.permissions;

import java.util.List;
import java.util.Map;

/**
 * 权限判定结果。deny > ask > allow。
 * 使用 sealed interface 保证类型完备性。
 *
 * @author BEAM
 */
public sealed interface PermissionDecision {

    record Allow(String message, Map<String, Object> updatedInput)
            implements PermissionDecision {}

    record Ask(String message, List<String> suggestions)
            implements PermissionDecision {}

    record Deny(String message, DecisionReason reason)
            implements PermissionDecision {}
}
