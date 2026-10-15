package com.patent.disclosure.workflow.handler;

import com.patent.disclosure.automation.KeyboardController;
import com.patent.disclosure.workflow.StepHandler;
import com.patent.disclosure.workflow.StepResult;
import com.patent.disclosure.workflow.WorkflowContext;
import com.patent.disclosure.workflow.WorkflowStep;
import org.springframework.stereotype.Component;

/** INPUT：键盘输入 targetValue（${VAR} 已由引擎替换）。 */
@Component
public class InputHandler implements StepHandler {

    private final KeyboardController keyboard;

    public InputHandler(KeyboardController keyboard) {
        this.keyboard = keyboard;
    }

    @Override
    public String actionType() {
        return "INPUT";
    }

    @Override
    public StepResult execute(WorkflowStep step, String target, WorkflowContext ctx, long start) {
        try {
            if (target == null || target.isBlank()) {
                return StepResult.fail(step.stepOrder(), step.actionType(), target,
                        "INPUT 内容为空", System.currentTimeMillis() - start);
            }
            keyboard.type(target);
            return StepResult.ok(step.stepOrder(), step.actionType(), target,
                    null, null, System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
