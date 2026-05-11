package com.learnclaudecode.permissions;

/**
 * 拒绝原因标记，用于审计和 DenialTracker。
 *
 * @author BEAM
 */
public record DecisionReason(String type, String detail) {
    public static DecisionReason rule(String detail) { return new DecisionReason("rule", detail); }
    public static DecisionReason safety(String detail) { return new DecisionReason("safety", detail); }
    public static DecisionReason mode(String detail) { return new DecisionReason("mode", detail); }
    public static DecisionReason userDenied() { return new DecisionReason("user", "denied by user"); }
}
