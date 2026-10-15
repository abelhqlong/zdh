package com.patent.disclosure.workflow;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置预设流程，与 mock/iphone-simulator.html 的页面文案一一对应。
 * 变量：${APPLE_ID}、${PASSWORD}。
 */
public final class WorkflowPresets {
    private WorkflowPresets() {}

    public static Map<String, WorkflowDefinition> all() {
        Map<String, WorkflowDefinition> map = new LinkedHashMap<>();
        map.put("preflight", new WorkflowDefinition("preflight",
                "刷机前置：Shadowrocket 配代理 → amg 一键新机（需 ${IP} 变量，如 192.168.1.10）", preflightSteps()));
        map.put("erase", new WorkflowDefinition("erase",
                "整机刷机：设置 → 通用 → 抹掉所有内容与设置", eraseSteps()));
        map.put("activate", new WorkflowDefinition("activate",
                "激活新机并登录全新 Apple ID（从 Hello 页面开始）", activateSteps()));
        map.put("poshmark", new WorkflowDefinition("poshmark",
                "打开 Poshmark，通过 Apple ID 注册（从桌面开始）", poshmarkSteps()));
        map.put("full", new WorkflowDefinition("full",
                "一键全流程：前置(Shadowrocket/amg/一键新机) → 刷机 → 激活+新 Apple ID → Poshmark 注册",
                fullSteps()));
        return map;
    }

    public static WorkflowDefinition get(String name) {
        return all().get(name == null ? "" : name.toLowerCase());
    }

    /* ---------- ⓪ 刷机前置：Shadowrocket 配代理 → amg 一键新机（起点：桌面） ---------- */
    private static List<WorkflowStep> preflightSteps() {
        List<WorkflowStep> s = new ArrayList<>();
        int i = 1;
        s.add(st(i++, "CLICK", "Shadowrocket", 3, 800L));       // 进入 Shadowrocket
        s.add(st(i++, "WAIT", null, 0, 800L));
        s.add(st(i++, "CLICK", "Global Routing", 3, 800L));     // 进入全局路由设置
        s.add(st(i++, "WAIT", null, 0, 600L));
        s.add(st(i++, "CLICK", "Enable Fallback", 3, 800L));    // 开启备用节点自动切换
        s.add(st(i++, "WAIT", null, 0, 400L));
        s.add(st(i++, "CLICK", "Proxy", 3, 800L));              // 全局代理模式
        s.add(st(i++, "WAIT", null, 0, 300L));
        s.add(st(i++, "CLICK", "Back", 3, 800L));               // 返回 Shadowrocket 主页
        s.add(st(i++, "WAIT", null, 0, 600L));
        s.add(st(i++, "CLICK", "MIYAIP 订阅", 3, 800L));        // 展开订阅节点列表
        s.add(st(i++, "WAIT", null, 0, 600L));
        s.add(st(i++, "CLICK", "MIYAIP-7", 3, 800L));           // 选中节点（状态栏出现 VPN）
        s.add(st(i++, "WAIT", null, 0, 800L));
        s.add(st(i++, "CLICK", "Home", 3, 800L));               // 返回桌面
        s.add(st(i++, "WAIT", null, 0, 500L));
        s.add(st(i++, "CHECK", "VPN", 3, 1000L, Map.of("expect", "present"))); // 确认代理已连接
        s.add(st(i++, "CLICK", "amg", 3, 800L));                // 进入 amg 应用（Dock 栏）
        s.add(st(i++, "WAIT", null, 0, 800L));
        s.add(st(i++, "CLICK", "一键新机", 3, 800L));            // 点击一键新机
        s.add(st(i++, "WAIT", null, 0, 1200L));
        s.add(st(i++, "CHECK", "正在操作", 3, 1000L, Map.of("expect", "present"))); // 加载提示
        s.add(st(i++, "WAIT", null, 0, 5000L));                 // 等待操作完成（真实约 5~8s）
        s.add(st(i++, "CHECK", "设备信息", 3, 1000L, Map.of("expect", "present"))); // 回主菜单
        s.add(st(i++, "CLICK", "Home", 3, 800L));               // 回桌面（衔接刷机）
        s.add(st(i, "WAIT", null, 0, 500L));
        return s;
    }

    /* ---------- ① 刷机 ---------- */
    private static List<WorkflowStep> eraseSteps() {
        List<WorkflowStep> s = new ArrayList<>();
        int i = 1;
        s.add(st(i++, "CLICK", "Settings", 3, 500L));
        s.add(st(i++, "CLICK", "General", 3, 500L));
        s.add(st(i++, "CLICK", "Transfer or Reset iPhone", 3, 500L));
        s.add(st(i++, "CLICK", "Erase All Content and Settings", 3, 500L));
        s.add(st(i++, "CLICK", "Erase", 3, 500L));
        s.add(st(i++, "WAIT", null, 0, 3200L));          // 抹除动画（模拟器 2.6s 自动进入 Hello）
        s.add(st(i, "CHECK", "Hello", 5, 1000L));        // 确认已进入激活界面
        return s;
    }

    /* ---------- ② 激活 + 新 Apple ID（起点：Hello） ---------- */
    private static List<WorkflowStep> activateSteps() {
        List<WorkflowStep> s = new ArrayList<>();
        int i = 1;
        s.add(st(i++, "CLICK", "English", 3, 500L));           // 选择语言
        s.add(st(i++, "CLICK", "Set Up Manually", 3, 500L));
        s.add(st(i++, "CLICK", "Skip", 3, 500L));              // Wi-Fi 页右上 Skip
        s.add(st(i++, "CLICK", "Apple ID", 3, 500L, Map.of("index", 1))); // 输入框 label（第 1 个匹配是顶部标题）
        s.add(st(i++, "INPUT", "${APPLE_ID}", 0, 0L));
        s.add(st(i++, "CLICK", "Continue", 3, 500L));
        s.add(st(i++, "CLICK", "Password", 3, 500L, Map.of("index", 1))); // 密码输入框 label（第 1 个匹配是标题）
        s.add(st(i++, "INPUT", "${PASSWORD}", 0, 0L));
        s.add(st(i++, "CLICK", "Sign In", 3, 500L, Map.of("index", 1))); // 第 2 个匹配是按钮（第 1 个是顶部导航标题）
        s.add(st(i++, "CLICK", "Agree", 3, 500L));             // 条款
        s.add(st(i++, "CLICK", "Continue", 3, 500L));          // 创建密码页
        s.add(st(i, "CHECK", "Poshmark", 5, 1000L));           // 已回桌面
        return s;
    }

    /* ---------- ③ Poshmark 注册（起点：桌面） ---------- */
    private static List<WorkflowStep> poshmarkSteps() {
        List<WorkflowStep> s = new ArrayList<>();
        int i = 1;
        s.add(st(i++, "CLICK", "Poshmark", 3, 500L));          // 桌面 App
        s.add(st(i++, "CLICK", "Continue with Apple", 3, 500L));
        s.add(st(i++, "CLICK", "Continue", 3, 500L));          // Apple 授权弹窗
        s.add(st(i, "CHECK", "My Closet", 5, 1000L));          // 注册成功
        return s;
    }

    /* ---------- 一键全流程 ---------- */
    private static List<WorkflowStep> fullSteps() {
        List<WorkflowStep> s = new ArrayList<>();
        int i = 1;
        for (WorkflowStep step : preflightSteps()) s.add(renumber(step, i++));
        for (WorkflowStep step : eraseSteps()) s.add(renumber(step, i++));
        for (WorkflowStep step : activateSteps()) s.add(renumber(step, i++));
        for (WorkflowStep step : poshmarkSteps()) s.add(renumber(step, i++));
        return s;
    }

    private static WorkflowStep renumber(WorkflowStep step, int order) {
        return new WorkflowStep(order, step.actionType(), step.targetType(),
                step.targetValue(), step.retryCount(), step.timeout(), step.x(), step.y(), step.config());
    }

    private static WorkflowStep st(int order, String action, String target,
                                   Integer retries, Long timeout) {
        return st(order, action, target, retries, timeout, null, null, Map.of());
    }

    private static WorkflowStep st(int order, String action, String target,
                                   Integer retries, Long timeout, Map<String, Object> config) {
        return st(order, action, target, retries, timeout, null, null, config);
    }

    private static WorkflowStep st(int order, String action, String target,
                                   Integer retries, Long timeout,
                                   Integer x, Integer y, Map<String, Object> config) {
        return new WorkflowStep(order, action, "TEXT", target, retries, timeout, x, y, config);
    }
}
