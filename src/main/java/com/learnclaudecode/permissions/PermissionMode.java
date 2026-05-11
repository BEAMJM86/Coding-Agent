package com.learnclaudecode.permissions;

/**
 * 会话级权限模式，启动时确定，影响整个生命周期。
 *
 * @author BEAM
 */
public enum PermissionMode {
    /** 正常：沙箱自动放行安全操作，未命中规则弹确认 */
    DEFAULT,
    /** 只读规划模式：Bash 和写操作被拒绝 */
    PLAN,
    /** 信任编辑操作：自动放行编辑类工具 */
    ACCEPT_EDITS,
    /** 跳过全部检查（需通过 BypassSafetyGate） */
    BYPASS,
    /** 无交互：ask 自动变 deny */
    DONT_ASK;
}
