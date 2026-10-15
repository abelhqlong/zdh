package com.patent.disclosure.workflow;

/**
 * 步骤执行器：一个 actionType 对应一个实现类（开闭原则，新类型=新类）。
 * 引擎按 actionType 从注册表找到执行器调用。
 */
public interface StepHandler {

    /** 支持的步骤类型（大写，如 CLICK / FIND_TEXT / INPUT / IF_TEXT） */
    String actionType();

    /**
     * 执行步骤。
     *
     * @param step   当前步骤
     * @param target 已做变量替换的目标值（可能为 null）
     * @param ctx    执行上下文
     * @param start  步骤开始时间戳
     */
    StepResult execute(WorkflowStep step, String target, WorkflowContext ctx, long start);
}
