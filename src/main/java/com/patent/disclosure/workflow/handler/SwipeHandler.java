package com.patent.disclosure.workflow.handler;

import com.patent.disclosure.automation.MouseController;
import com.patent.disclosure.workflow.StepConfigs;
import com.patent.disclosure.workflow.StepHandler;
import com.patent.disclosure.workflow.StepResult;
import com.patent.disclosure.workflow.WorkflowContext;
import com.patent.disclosure.workflow.WorkflowStep;
import org.springframework.stereotype.Component;

/** SWIPE：从 (x,y) 拖到 config.toX/config.toY，config.duration 毫秒。 */
@Component
public class SwipeHandler implements StepHandler {

    private final MouseController mouse;

    public SwipeHandler(MouseController mouse) {
        this.mouse = mouse;
    }

    @Override
    public String actionType() {
        return "SWIPE";
    }

    @Override
    public StepResult execute(WorkflowStep step, String target, WorkflowContext ctx, long start) {
        try {
            if (step.x() == null || step.y() == null) {
                return StepResult.fail(step.stepOrder(), step.actionType(), null,
                        "SWIPE 需要起点 x/y", System.currentTimeMillis() - start);
            }
            int toX = StepConfigs.configInt(step, "toX", step.x());
            int toY = StepConfigs.configInt(step, "toY", step.y());
            long duration = StepConfigs.configLong(step, "duration", 300);
            mouse.drag(step.x(), step.y(), toX, toY, duration);
            return StepResult.ok(step.stepOrder(), step.actionType(), null,
                    toX, toY, System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
