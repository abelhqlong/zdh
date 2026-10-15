package com.patent.disclosure.workflow.handler;

import com.patent.disclosure.automation.MirrorRegionService;
import com.patent.disclosure.automation.ScreenCapture;
import com.patent.disclosure.ocr.OcrResult;
import com.patent.disclosure.ocr.OcrService;
import com.patent.disclosure.workflow.StepConfigs;
import com.patent.disclosure.workflow.StepHandler;
import com.patent.disclosure.workflow.StepResult;
import com.patent.disclosure.workflow.WorkflowContext;
import com.patent.disclosure.workflow.WorkflowStep;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * EXTRACT_TEXT：从 OCR 识别文本中按正则提取内容存入变量。
 *
 *   targetValue     = 正则表达式（如 \d{6} 或 验证码[：:]\s*(\d{4,6})）
 *   config.variable = 存储变量名（必填，之后可用 ${变量名} 引用）
 *   config.group    = 捕获组（默认 1；0 = 整个匹配；负数 = 从尾部数）
 *   config.index    = 第几个匹配（默认 0 第一个）
 *   config.region   = 指定识别区域
 *
 * 典型场景：抓取验证码、抓取生成的账号/编号，供后续 INPUT / CHECK 使用。
 */
@Component
public class ExtractTextHandler extends BaseOcrHandler implements StepHandler {

    public ExtractTextHandler(OcrService ocr, ScreenCapture screenCapture, MirrorRegionService regionService) {
        super(ocr, screenCapture, regionService);
    }

    @Override
    public String actionType() {
        return "EXTRACT_TEXT";
    }

    @Override
    public StepResult execute(WorkflowStep step, String target, WorkflowContext ctx, long start) {
        int retries = step.retryCount() == null ? 3 : step.retryCount();
        long interval = step.timeout() == null ? 500 : step.timeout();
        String variable = StepConfigs.configStr(step, "variable", null);
        int group = StepConfigs.configInt(step, "group", 1);
        int index = StepConfigs.configInt(step, "index", 0);

        if (variable == null || variable.isBlank()) {
            return StepResult.fail(step.stepOrder(), step.actionType(), target,
                    "EXTRACT_TEXT 需要 config.variable（存储变量名）", System.currentTimeMillis() - start);
        }
        if (target == null || target.isBlank()) {
            return StepResult.fail(step.stepOrder(), step.actionType(), target,
                    "EXTRACT_TEXT 需要正则表达式", System.currentTimeMillis() - start);
        }
        Pattern pattern;
        try {
            pattern = Pattern.compile(target);
        } catch (Exception e) {
            return StepResult.fail(step.stepOrder(), step.actionType(), target,
                    "正则表达式无效: " + e.getMessage(), System.currentTimeMillis() - start);
        }

        String lastOcr = null;
        for (int i = 0; i <= retries; i++) {
            OcrResult result = recognizeRegion(step);
            lastOcr = result.text();
            Matcher m = pattern.matcher(result.text() == null ? "" : result.text());
            java.util.List<String> hits = new java.util.ArrayList<>();
            while (m.find()) {
                int g = group < 0 ? m.groupCount() + 1 + group : group;
                g = Math.max(0, Math.min(g, m.groupCount()));
                hits.add(m.group(g));
            }
            int idx = index < 0 ? hits.size() + index : index;
            if (idx >= 0 && idx < hits.size()) {
                String value = hits.get(idx);
                ctx.setVar(variable, value);
                return StepResult.ok(step.stepOrder(), step.actionType(), target,
                        null, null, System.currentTimeMillis() - start);
            }
            sleep(interval);
        }
        return StepResult.failWithOcr(step.stepOrder(), step.actionType(), target,
                "未从识别文本中提取到匹配: " + target, lastOcr, System.currentTimeMillis() - start);
    }
}
