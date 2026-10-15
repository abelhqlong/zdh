package com.patent.disclosure.workflow.handler;

import com.patent.disclosure.workflow.StepConfigs;
import com.patent.disclosure.workflow.StepHandler;
import com.patent.disclosure.workflow.StepResult;
import com.patent.disclosure.workflow.WorkflowContext;
import com.patent.disclosure.workflow.WorkflowStep;
import org.springframework.stereotype.Component;

import java.util.Random;

/** WAIT：固定等待 timeout 毫秒，或随机等待 [minMs, maxMs] 毫秒。 */
@Component
public class WaitHandler implements StepHandler {

    private final Random random = new Random();

    @Override
    public String actionType() {
        return "WAIT";
    }

    @Override
    public StepResult execute(WorkflowStep step, String target, WorkflowContext ctx, long start) {
        long ms;
        int minMs = StepConfigs.configInt(step, "minMs", -1);
        int maxMs = StepConfigs.configInt(step, "maxMs", -1);
        if (minMs > 0 && maxMs >= minMs) {
            // 随机等待
            ms = minMs + random.nextInt(maxMs - minMs + 1);
        } else {
            // 固定等待
            ms = step.timeout() == null ? 1000 : step.timeout();
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return StepResult.ok(step.stepOrder(), step.actionType(), "等待 " + ms + "ms",
                null, null, System.currentTimeMillis() - start);
    }
}
