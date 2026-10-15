package com.patent.disclosure.workflow.handler;

import com.patent.disclosure.automation.MirrorRegionService;
import com.patent.disclosure.automation.MouseController;
import com.patent.disclosure.automation.ScreenCapture;
import com.patent.disclosure.ocr.OcrElement;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CLICK_FASTEST：在节点列表里自动挑延时最低的并点击。
 *
 * 用法：
 *   targetValue = 节点名前缀（如 "miya-chicago-"）
 *   自动 OCR 区域内所有元素，把节点名与同行右侧的 "Nms" 延时配对，
 *   选延时最小的节点点击其中心。
 *
 * 适用场景：Shadowrocket Local Servers 这类"每行一个节点 + 右边显示 ms"的列表。
 */
@Component
public class ClickFastestNodeHandler extends BaseOcrHandler implements StepHandler {

    private static final Pattern MS_PATTERN = Pattern.compile("(\\d+)\\s*ms");

    private final MouseController mouse;

    public ClickFastestNodeHandler(OcrService ocr,
                                   ScreenCapture screenCapture,
                                   MirrorRegionService regionService,
                                   MouseController mouse) {
        super(ocr, screenCapture, regionService);
        this.mouse = mouse;
    }

    @Override
    public String actionType() {
        return "CLICK_FASTEST";
    }

    @Override
    public StepResult execute(WorkflowStep step, String target, WorkflowContext ctx, long start) {
        int retries = step.retryCount() == null ? 3 : step.retryCount();
        long interval = step.timeout() == null ? 1000 : step.timeout();
        int dx = StepConfigs.configInt(step, "dx", 0);
        int dy = StepConfigs.configInt(step, "dy", 0);

        if (target == null || target.isBlank()) {
            return StepResult.fail(step.stepOrder(), actionType(), target,
                    "CLICK_FASTEST 需要节点名前缀（如 miya-chicago-）", System.currentTimeMillis() - start);
        }

        for (int i = 0; i <= retries; i++) {
            OcrResult result = recognizeRegion(step);

            // 收集所有 "Nms" 延时元素
            List<OcrElement> msElements = new ArrayList<>();
            for (OcrElement e : result.elements()) {
                if (MS_PATTERN.matcher(e.text().trim()).find()) msElements.add(e);
            }

            // 对每个节点名元素，找同行右侧的延时
            OcrElement bestNode = null;
            int bestMs = Integer.MAX_VALUE;
            for (OcrElement e : result.elements()) {
                String t = e.text().trim();
                if (!t.contains(target)) continue;
                for (OcrElement ms : msElements) {
                    int nodeCenterY = e.screenY() + e.screenHeight() / 2;
                    int msCenterY = ms.screenY() + ms.screenHeight() / 2;
                    if (Math.abs(msCenterY - nodeCenterY) < 15 && ms.screenX() > e.screenX()) {
                        Matcher m = MS_PATTERN.matcher(ms.text().trim());
                        if (m.find()) {
                            int val = Integer.parseInt(m.group(1));
                            if (val < bestMs) {
                                bestMs = val;
                                bestNode = e;
                            }
                        }
                        break;
                    }
                }
            }

            if (bestNode != null) {
                int cx = bestNode.centerScreenX() + dx;
                int cy = bestNode.centerScreenY() + dy;
                try {
                    mouse.click(cx, cy);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                return StepResult.ok(step.stepOrder(), actionType(),
                        bestNode.text() + " (" + bestMs + "ms)", cx, cy,
                        System.currentTimeMillis() - start);
            }
            sleep(interval);
        }
        return StepResult.fail(step.stepOrder(), actionType(), target,
                "未找到带延时的节点列表（前缀: " + target + "）", System.currentTimeMillis() - start);
    }
}
