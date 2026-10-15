package com.patent.disclosure.workflow.handler;

import com.patent.disclosure.automation.MirrorRegionService;
import com.patent.disclosure.automation.ScreenCapture;
import com.patent.disclosure.ocr.OcrMatcher;
import com.patent.disclosure.ocr.OcrResult;
import com.patent.disclosure.ocr.OcrService;
import com.patent.disclosure.workflow.StepConfigs;
import com.patent.disclosure.workflow.StepHandler;
import com.patent.disclosure.workflow.StepResult;
import com.patent.disclosure.workflow.WorkflowContext;
import com.patent.disclosure.workflow.WorkflowStep;
import org.springframework.stereotype.Component;

/**
 * IF_TEXT：条件判断（OCR 轮询文字出现/不出现），
 * 返回 success = 条件成立（present 时为出现，absent 时为不出现）。
 * 跳转由引擎按 config.gotoOnTrue / gotoOnFalse 执行。
 */
@Component
public class IfTextHandler extends BaseOcrHandler implements StepHandler {

    public IfTextHandler(OcrService ocr, ScreenCapture screenCapture, MirrorRegionService regionService) {
        super(ocr, screenCapture, regionService);
    }

    @Override
    public String actionType() {
        return "IF_TEXT";
    }

    @Override
    public StepResult execute(WorkflowStep step, String target, WorkflowContext ctx, long start) {
        int retries = step.retryCount() == null ? 1 : step.retryCount();
        long interval = step.timeout() == null ? 500 : step.timeout();
        boolean expectAbsent = "absent".equalsIgnoreCase(StepConfigs.configStr(step, "expect", "present"));
        int index = StepConfigs.configInt(step, "index", 0);

        if (target == null || target.isBlank()) {
            return StepResult.fail(step.stepOrder(), step.actionType(), target,
                    "IF_TEXT 需要目标文字", System.currentTimeMillis() - start);
        }
        String lastOcr = null;
        for (int i = 0; i <= retries; i++) {
            OcrResult result = recognizeRegion(step);
            lastOcr = result.text();
            boolean hit = OcrMatcher.matchAt(result.elements(), target, index).isPresent();
            if (expectAbsent ? !hit : hit) {
                return StepResult.ok(step.stepOrder(), step.actionType(), target,
                        null, null, System.currentTimeMillis() - start);
            }
            sleep(interval);
        }
        // 轮询结束仍未满足 → 条件不成立
        return StepResult.failWithOcr(step.stepOrder(), step.actionType(), target,
                "IF_TEXT 条件未满足: " + target + (expectAbsent ? "（应消失）" : "（应出现）"),
                lastOcr, System.currentTimeMillis() - start);
    }
}
