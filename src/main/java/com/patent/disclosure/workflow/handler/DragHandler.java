package com.patent.disclosure.workflow.handler;

import com.patent.disclosure.automation.MouseController;
import com.patent.disclosure.workflow.StepConfigs;
import com.patent.disclosure.workflow.StepHandler;
import com.patent.disclosure.workflow.StepResult;
import com.patent.disclosure.workflow.WorkflowContext;
import com.patent.disclosure.workflow.WorkflowStep;
import org.springframework.stereotype.Component;

/**
 * DRAG：从 (x1,y1) 拖到 (x2,y2)，用于滑动/滚动。
 * 参数通过 config 传递：x1, y1, x2, y2, durationMs(默认300)
 */
@Component
public class DragHandler implements StepHandler {

    private final MouseController mouse;

    public DragHandler(MouseController mouse) {
        this.mouse = mouse;
    }

    @Override
    public String actionType() {
        return "DRAG";
    }

    @Override
    public StepResult execute(WorkflowStep step, String target, WorkflowContext ctx, long start) {
        try {
            int x1 = StepConfigs.configInt(step, "x1", 0);
            int y1 = StepConfigs.configInt(step, "y1", 0);
            int x2 = StepConfigs.configInt(step, "x2", 0);
            int y2 = StepConfigs.configInt(step, "y2", 0);
            long dur = StepConfigs.configLong(step, "durationMs", 300);
            mouse.drag(x1, y1, x2, y2, dur);
            return StepResult.ok(step.stepOrder(), step.actionType(), target,
                    x2, y2, System.currentTimeMillis() - start);
        } catch (Exception e) {
            return StepResult.fail(step.stepOrder(), step.actionType(), target,
                    "DRAG 失败: " + e.getMessage(), System.currentTimeMillis() - start);
        }
    }
}
