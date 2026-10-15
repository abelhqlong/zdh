package com.patent.disclosure.workflow.handler;

import com.patent.disclosure.workflow.StepConfigs;
import com.patent.disclosure.workflow.StepHandler;
import com.patent.disclosure.workflow.StepResult;
import com.patent.disclosure.workflow.WorkflowContext;
import com.patent.disclosure.workflow.WorkflowStep;
import org.springframework.stereotype.Component;

/** WAIT_CODE：等待人工处理（验证码/授权），config.waitMs 毫秒后继续。 */
@Component
public class WaitCodeHandler implements StepHandler {

    @Override
    public String actionType() {
        return "WAIT_CODE";
    }

    @Override
    public StepResult execute(WorkflowStep step, String target, WorkflowContext ctx, long start) {
        long ms = StepConfigs.configLong(step, "waitMs", 8000);
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return StepResult.waiting(step.stepOrder(), step.actionType(), null,
                "已等待 " + ms + "ms 供人工完成验证码/授权，流程继续", System.currentTimeMillis() - start);
    }
}
