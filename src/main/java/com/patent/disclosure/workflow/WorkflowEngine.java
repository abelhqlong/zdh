package com.patent.disclosure.workflow;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流程执行引擎（L1 内核）：
 *
 * 1. 动作步骤由 handler 注册表分发（开闭原则：新 actionType = 新 StepHandler 实现类）；
 * 2. 内置控制流：LABEL / GOTO / REPEAT / IF_TEXT；
 * 3. 每步可配失败策略 config.onFail 与成功策略 config.onSuccess：
 *      next / skip / stop / goto:LABEL，默认失败中断、成功继续；
 * 4. 防死循环：单次执行最多 WorkflowContext.MAX_STEPS 步。
 *
 * 控制流字段均放在 config 中（不改变 WorkflowStep 结构，旧流程定义完全兼容）：
 *   LABEL       config.label=标签名（无操作，仅标记位置）
 *   GOTO        config.label=目标标签名
 *   REPEAT      config.times=循环次数, config.goto=循环体起始标签（置于循环体之后，先执行后判断）
 *   IF_TEXT     targetValue=判定文字；config.expect=present|absent（默认 present）；
 *               config.gotoOnTrue / config.gotoOnFalse = 跳转标签（不填则继续下一步）
 *   CLICK/INPUT 等：config.onFail / config.onSuccess = next | skip | stop | goto:LABEL
 */
@Service
public class WorkflowEngine {

    private final Map<String, StepHandler> handlers = new HashMap<>();

    public WorkflowEngine(List<StepHandler> handlerList) {
        for (StepHandler h : handlerList) {
            if (h.actionType() != null) {
                handlers.put(h.actionType().toUpperCase(), h);
            }
        }
    }

    public WorkflowReport execute(WorkflowDefinition definition, Map<String, String> variables) {
        return execute(definition, variables, 0, Integer.MAX_VALUE);
    }

    /** 支持从指定步骤下标开始执行（用于注册主体跑完后，注入验证码续跑收尾段）。 */
    public WorkflowReport execute(WorkflowDefinition definition, Map<String, String> variables, int startIndex) {
        return execute(definition, variables, startIndex, Integer.MAX_VALUE);
    }

    public WorkflowReport execute(WorkflowDefinition definition, Map<String, String> variables, int startIndex, int endIndex) {
        return execute(definition, variables, startIndex, endIndex, null);
    }

    /** 步骤进度回调：每执行完一步(含控制流)回调一次，供前端实时展示进度。 */
    public interface ProgressListener {
        void onStep(WorkflowStep step, String target, StepResult result);
    }

    /** 支持 [startIndex, endIndex) 区间执行：主体(endIndex 截断)、收尾(startIndex 续跑)共用。 */
    public WorkflowReport execute(WorkflowDefinition definition, Map<String, String> variables, int startIndex, int endIndex, ProgressListener listener) {
        long start = System.currentTimeMillis();
        WorkflowContext ctx = new WorkflowContext(variables);
        List<WorkflowStep> steps = definition.steps();

        // 建立标签索引（label → 数组下标）
        for (int i = 0; i < steps.size(); i++) {
            WorkflowStep s = steps.get(i);
            if ("LABEL".equalsIgnoreCase(actionOf(s))) {
                ctx.indexLabel(StepConfigs.configStr(s, "label", null), i);
            }
        }

        List<StepResult> results = new ArrayList<>();
        boolean interrupted = false;
        int cursor = Math.max(0, Math.min(startIndex, steps.size()));
        int cap = Math.min(endIndex, steps.size());

        while (cursor < cap) {
            if (ctx.bumpExecuted() > WorkflowContext.MAX_STEPS) {
                interrupted = true;
                break;
            }
            WorkflowStep step = steps.get(cursor);
            String action = actionOf(step);
            long stepStart = System.currentTimeMillis();
            String target = ctx.resolve(step.targetValue());

            // ---- 控制流：LABEL（无操作） ----
            if ("LABEL".equals(action)) {
                cursor++;
                continue;
            }

            // ---- 控制流：GOTO ----
            if ("GOTO".equals(action)) {
                String label = StepConfigs.configStr(step, "label", null);
                Integer targetIndex = ctx.labelIndex(label);
                if (targetIndex == null) {
                    results.add(StepResult.fail(step.stepOrder(), action, label,
                            "GOTO 目标标签不存在: " + label, System.currentTimeMillis() - stepStart));
                    interrupted = true;
                    break;
                }
                results.add(StepResult.ok(step.stepOrder(), action, label,
                        null, null, System.currentTimeMillis() - stepStart));
                notifyProgress(listener, step, label, results.get(results.size() - 1));
                cursor = targetIndex;
                continue;
            }

            // ---- 控制流：REPEAT（先执行后判断；置于循环体之后） ----
            if ("REPEAT".equals(action)) {
                int times = StepConfigs.configInt(step, "times", 1);
                String gotoLabel = StepConfigs.configStr(step, "goto", null);
                int count = ctx.repeatCount(gotoLabel) + 1;
                ctx.incRepeat(gotoLabel);
                if (gotoLabel != null && ctx.hasLabel(gotoLabel) && count < times) {
                    results.add(StepResult.ok(step.stepOrder(), action,
                            "循环 " + count + "/" + times + " → " + gotoLabel,
                            null, null, System.currentTimeMillis() - stepStart));
                    notifyProgress(listener, step, "循环 " + count + "/" + times, results.get(results.size() - 1));
                    cursor = ctx.labelIndex(gotoLabel);
                } else {
                    ctx.resetRepeat(gotoLabel);
                    results.add(StepResult.ok(step.stepOrder(), action,
                            "循环结束（" + count + "/" + times + "）", null, null,
                            System.currentTimeMillis() - stepStart));
                    notifyProgress(listener, step, "循环结束", results.get(results.size() - 1));
                    cursor++;
                }
                continue;
            }

            // ---- 控制流：IF_TEXT（handler 判条件，引擎跳转） ----
            if ("IF_TEXT".equals(action)) {
                StepResult r = runHandler(step, target, ctx, stepStart);
                results.add(r);
                notifyProgress(listener, step, target, r);
                boolean condTrue = r.success();
                String jump = condTrue
                        ? StepConfigs.configStr(step, "gotoOnTrue", null)
                        : StepConfigs.configStr(step, "gotoOnFalse", null);
                if (jump != null) {
                    Integer targetIndex = ctx.labelIndex(jump);
                    if (targetIndex == null) {
                        interrupted = true;
                        break;
                    }
                    cursor = targetIndex;
                } else {
                    cursor++;
                }
                continue;
            }

            // ---- 普通动作：handler 执行 + 成功/失败策略 ----
            StepResult r = runHandler(step, target, ctx, stepStart);
            results.add(r);
            notifyProgress(listener, step, target, r);
            String policy = r.success()
                    ? StepConfigs.configStr(step, "onSuccess", null)
                    : StepConfigs.configStr(step, "onFail", null);
            if (policy == null || policy.isBlank()) {
                if (!r.success()) {
                    interrupted = true;
                    break;
                }
                cursor++;
                continue;
            }
            String p = policy.trim().toLowerCase();
            switch (p) {
                case "stop" -> {
                    if (r.success()) {
                        // 成功后主动停止：流程视为成功完成
                        cursor = steps.size();
                    } else {
                        interrupted = true;
                    }
                }
                case "skip" -> cursor++;
                default -> {
                    if (p.startsWith("goto:")) {
                        String label = p.substring(5).trim();
                        Integer targetIndex = ctx.labelIndex(label);
                        if (targetIndex == null) {
                            interrupted = true;
                        } else {
                            cursor = targetIndex;
                        }
                    } else if ("next".equals(p) || p.isEmpty()) {
                        cursor++;
                    } else if (!r.success()) {
                        interrupted = true;
                    } else {
                        cursor++;
                    }
                }
            }
        }

        boolean ok = !interrupted && !results.isEmpty() && results.stream().allMatch(StepResult::success);
        return new WorkflowReport(definition.name(), ok, interrupted, results, System.currentTimeMillis() - start);
    }

    private void notifyProgress(ProgressListener listener, WorkflowStep step, String target, StepResult r) {
        if (listener != null) {
            try {
                listener.onStep(step, target, r);
            } catch (Exception ignored) {
            }
        }
    }

    private StepResult runHandler(WorkflowStep step, String target, WorkflowContext ctx, long start) {
        StepHandler h = handlers.get(actionOf(step));
        if (h == null) {
            return StepResult.fail(step.stepOrder(), step.actionType(), target,
                    "未知步骤类型: " + step.actionType(), System.currentTimeMillis() - start);
        }
        try {
            return h.execute(step, target, ctx, start);
        } catch (Exception e) {
            return StepResult.fail(step.stepOrder(), step.actionType(), target,
                    e.getClass().getSimpleName() + ": " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private static String actionOf(WorkflowStep step) {
        String a = step.actionType();
        return a == null ? "" : a.trim().toUpperCase();
    }
}
