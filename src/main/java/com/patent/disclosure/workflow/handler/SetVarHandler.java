package com.patent.disclosure.workflow.handler;

import com.patent.disclosure.workflow.StepConfigs;
import com.patent.disclosure.workflow.StepHandler;
import com.patent.disclosure.workflow.StepResult;
import com.patent.disclosure.workflow.WorkflowContext;
import com.patent.disclosure.workflow.WorkflowStep;
import org.springframework.stereotype.Component;

/**
 * SET_VAR：把值写入变量（后续步骤用 ${变量名} 引用）。
 *
 *   targetValue   = 变量名
 *   config.value  = 值表达式，支持 ${VAR} 嵌套（如 ${RANDOM_EMAIL}、${PREFIX}-${RANDOM_NUMBER:4}）
 *
 * 典型场景：把内置随机变量绑定到语义化变量名（如 userEmail），
 * 或拼接组合值（如 ${RANDOM_USER}@icloud.com）。
 */
@Component
public class SetVarHandler implements StepHandler {

    @Override
    public String actionType() {
        return "SET_VAR";
    }

    @Override
    public StepResult execute(WorkflowStep step, String target, WorkflowContext ctx, long start) {
        String name = target;
        if (name == null || name.isBlank()) {
            return StepResult.fail(step.stepOrder(), step.actionType(), target,
                    "SET_VAR 目标需填变量名", System.currentTimeMillis() - start);
        }
        String raw = StepConfigs.configStr(step, "value", "");
        String value = ctx.resolve(raw);
        ctx.setVar(name, value);
        return StepResult.ok(step.stepOrder(), step.actionType(), name,
                null, null, System.currentTimeMillis() - start);
    }
}
