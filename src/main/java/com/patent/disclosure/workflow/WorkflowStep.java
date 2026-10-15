package com.patent.disclosure.workflow;

import java.util.Map;

/**
 * 流程步骤定义。
 *
 * actionType:
 *   FIND_TEXT  - OCR 轮询查找文字，成功返回坐标
 *   CLICK      - 有 x/y 直接点击；否则按文字 OCR 查找并点击（config.index 可指定第几个匹配）
 *   INPUT      - 键盘输入 targetValue（${VAR} 会被变量替换）
 *   SWIPE      - 从 (x,y) 拖到 config.toX/toY，config.duration 毫秒
 *   WAIT       - 等待 timeout 毫秒
 *   WAIT_CODE  - 等待人工处理（验证码/授权），config.waitMs 毫秒后继续
 *   CHECK      - OCR 轮询检查文字/关键词出现；config.expect=absent 时检查消失
 *   IF_TEXT    - 条件判断（config.expect=present|absent），跳转见引擎说明
 *   REPEAT     - 循环（config.times/goto），见引擎说明
 *   GOTO       - 跳转到 config.label 标记的步骤
 *   LABEL      - 无操作，config.label 标记位置供 GOTO/REPEAT/IF_TEXT 跳转
 *
 * 控制流与策略字段统一放在 config 中（保证旧流程 JSON 完全兼容）：
 *   config.label / config.onFail / config.onSuccess（next|skip|stop|goto:标签）
 *   详见 WorkflowEngine 类注释。
 */
public record WorkflowStep(
        int stepOrder,
        String actionType,
        String targetType,
        String targetValue,
        Integer retryCount,
        Long timeout,
        Integer x,
        Integer y,
        Map<String, Object> config
) {
    public WorkflowStep {
        if (config == null) config = Map.of();
    }
}
