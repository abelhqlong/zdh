package com.patent.disclosure.workflow.handler;

import com.patent.disclosure.automation.MirrorRegionService;
import com.patent.disclosure.automation.ScreenCapture;
import com.patent.disclosure.ocr.OcrElement;
import com.patent.disclosure.ocr.OcrMatcher;
import com.patent.disclosure.ocr.OcrResult;
import com.patent.disclosure.ocr.OcrService;
import com.patent.disclosure.workflow.StepConfigs;
import com.patent.disclosure.workflow.StepHandler;
import com.patent.disclosure.workflow.StepResult;
import com.patent.disclosure.workflow.WorkflowContext;
import com.patent.disclosure.workflow.WorkflowStep;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * FIND_TEXT：OCR 轮询查找文字，成功返回坐标（不点击）。
 * L2 扩展：config.dx/dy 返回偏移后坐标；config.region 指定区域。
 */
@Component
public class FindTextHandler extends BaseOcrHandler implements StepHandler {

    public FindTextHandler(OcrService ocr, ScreenCapture screenCapture, MirrorRegionService regionService) {
        super(ocr, screenCapture, regionService);
    }

    @Override
    public String actionType() {
        return "FIND_TEXT";
    }

    @Override
    public StepResult execute(WorkflowStep step, String target, WorkflowContext ctx, long start) {
        int retries = step.retryCount() == null ? 3 : step.retryCount();
        long interval = step.timeout() == null ? 500 : step.timeout();
        int index = StepConfigs.configInt(step, "index", 0);
        int dx = StepConfigs.configInt(step, "dx", 0);
        int dy = StepConfigs.configInt(step, "dy", 0);
        String lastOcr = null;
        for (int i = 0; i <= retries; i++) {
            OcrResult result = recognizeRegion(step);
            lastOcr = result.text();
            Optional<OcrElement> m = OcrMatcher.matchAt(result.elements(), target, index);
            if (m.isPresent()) {
                OcrElement e = m.get();
                return StepResult.ok(step.stepOrder(), step.actionType(), target,
                        e.centerScreenX() + dx, e.centerScreenY() + dy, System.currentTimeMillis() - start);
            }
            sleep(interval);
        }
        return StepResult.failWithOcr(step.stepOrder(), step.actionType(), target,
                "未找到文字: " + target, lastOcr, System.currentTimeMillis() - start);
    }
}
