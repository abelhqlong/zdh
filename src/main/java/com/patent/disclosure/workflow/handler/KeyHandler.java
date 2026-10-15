package com.patent.disclosure.workflow.handler;

import com.patent.disclosure.automation.KeyboardController;
import com.patent.disclosure.workflow.StepHandler;
import com.patent.disclosure.workflow.StepResult;
import com.patent.disclosure.workflow.WorkflowContext;
import com.patent.disclosure.workflow.WorkflowStep;
import org.springframework.stereotype.Component;

/**
 * KEY：发送按键。
 * 目标填按键名：ENTER / TAB / ESC / BACKSPACE / SPACE / HOME / END /
 * PAGE_UP / PAGE_DOWN / UP / DOWN / LEFT / RIGHT / F1-F12。
 */
@Component
public class KeyHandler implements StepHandler {

    private final KeyboardController keyboard;

    public KeyHandler(KeyboardController keyboard) {
        this.keyboard = keyboard;
    }

    @Override
    public String actionType() {
        return "KEY";
    }

    @Override
    public StepResult execute(WorkflowStep step, String target, WorkflowContext ctx, long start) {
        try {
            if (target == null || target.isBlank()) {
                return StepResult.fail(step.stepOrder(), step.actionType(), target,
                        "KEY 需要按键名（如 ENTER/TAB/ESC）", System.currentTimeMillis() - start);
            }
            keyboard.pressKey(target);
            return StepResult.ok(step.stepOrder(), step.actionType(), target,
                    null, null, System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
