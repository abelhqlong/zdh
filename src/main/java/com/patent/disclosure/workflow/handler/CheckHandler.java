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

import java.util.ArrayList;
import java.util.List;

/**
 * CHECK：OCR 轮询检查文字出现（默认）或消失（config.expect=absent）。
 * 目标取 targetValue + config.keywords 的并集，命中任意一个即成功。
 */
@Component
public class CheckHandler extends BaseOcrHandler implements StepHandler {

    public CheckHandler(OcrService ocr, ScreenCapture screenCapture, MirrorRegionService regionService) {
        super(ocr, screenCapture, regionService);
    }

    @Override
    public String actionType() {
        return "CHECK";
    }

    @Override
    public StepResult execute(WorkflowStep step, String target, WorkflowContext ctx, long start) {
        int retries = step.retryCount() == null ? 3 : step.retryCount();
        long interval = step.timeout() == null ? 500 : step.timeout();
        boolean expectAbsent = "absent".equalsIgnoreCase(StepConfigs.configStr(step, "expect", "present"));

        List<String> keywords = new ArrayList<>();
        if (target != null && !target.isBlank()) keywords.add(target);
        Object kw = step.config().get("keywords");
        if (kw instanceof List<?> list) {
            for (Object o : list) if (o != null) keywords.add(String.valueOf(o));
        }
        if (keywords.isEmpty()) {
            return StepResult.fail(step.stepOrder(), step.actionType(), target,
                    "CHECK 需要目标文字或 config.keywords", System.currentTimeMillis() - start);
        }

        String lastOcr = null;
        for (int i = 0; i <= retries; i++) {
            OcrResult result = recognizeRegion(step);
            lastOcr = result.text();
            String all = OcrMatcher.normalize(result.text());
            boolean hit = false;
            for (String k : keywords) {
                if (all.contains(OcrMatcher.normalize(k))) {
                    hit = true;
                    break;
                }
            }
            if (expectAbsent ? !hit : hit) {
                return StepResult.ok(step.stepOrder(), step.actionType(),
                        expectAbsent ? null : (keywords.get(0)),
                        null, null, System.currentTimeMillis() - start);
            }
            sleep(interval);
        }
        return StepResult.failWithOcr(step.stepOrder(), step.actionType(), target,
                (expectAbsent ? "检查未通过，文字仍存在: " : "检查未通过，未找到关键词: ") + keywords,
                lastOcr, System.currentTimeMillis() - start);
    }
}
