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
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CLICK_RANDOM：在节点列表里随机挑一个带延时的节点点击。
 *
 * 用法：
 *   targetValue = 节点名前缀（如 "miya-chicago-"），留空则选所有带延时的节点
 *   自动 OCR 区域内所有元素，把节点名与同行右侧的 "Nms" 延时配对，
 *   随机选一个节点点击其中心。
 */
@Component
public class ClickRandomNodeHandler extends BaseOcrHandler implements StepHandler {

    private static final Pattern MS_PATTERN = Pattern.compile("(\\d+)\\s*ms");

    private final MouseController mouse;
    private final Random random = new Random();
    private String lastChosenNode = null; // 记录上次点的节点，避免重复

    public ClickRandomNodeHandler(OcrService ocr,
                                   ScreenCapture screenCapture,
                                   MirrorRegionService regionService,
                                   MouseController mouse) {
        super(ocr, screenCapture, regionService);
        this.mouse = mouse;
    }

    @Override
    public String actionType() {
        return "CLICK_RANDOM";
    }

    @Override
    public StepResult execute(WorkflowStep step, String target, WorkflowContext ctx, long start) {
        int retries = step.retryCount() == null ? 3 : step.retryCount();
        long interval = step.timeout() == null ? 1000 : step.timeout();
        int dx = StepConfigs.configInt(step, "dx", 0);
        int dy = StepConfigs.configInt(step, "dy", 0);

        for (int i = 0; i <= retries; i++) {
            OcrResult result = recognizeRegion(step);

            // 收集所有 "Nms" 延时元素
            List<OcrElement> msElements = new ArrayList<>();
            for (OcrElement e : result.elements()) {
                if (MS_PATTERN.matcher(e.text().trim()).find()) msElements.add(e);
            }

            // 找所有匹配前缀的节点，配对同行延时
            List<OcrElement> matchedNodes = new ArrayList<>();
            for (OcrElement e : result.elements()) {
                String t = e.text().trim();
                if (target != null && !target.isBlank() && !t.contains(target)) continue;
                for (OcrElement ms : msElements) {
                    int nodeCenterY = e.screenY() + e.screenHeight() / 2;
                    int msCenterY = ms.screenY() + ms.screenHeight() / 2;
                    if (Math.abs(msCenterY - nodeCenterY) < 15 && ms.screenX() > e.screenX()) {
                        Matcher m = MS_PATTERN.matcher(ms.text().trim());
                        if (m.find()) {
                            matchedNodes.add(e);
                        }
                        break;
                    }
                }
            }

            if (!matchedNodes.isEmpty()) {
                // 优先避开上次点过的节点
                List<OcrElement> candidates = new ArrayList<>();
                for (OcrElement n : matchedNodes) {
                    if (lastChosenNode == null || !n.text().trim().equals(lastChosenNode)) {
                        candidates.add(n);
                    }
                }
                // 如果避开后只剩一个，就用全部
                if (candidates.isEmpty()) candidates = matchedNodes;

                OcrElement chosen = candidates.get(random.nextInt(candidates.size()));
                lastChosenNode = chosen.text().trim(); // 记录这次选的
                int cx = chosen.centerScreenX() + dx;
                int cy = chosen.centerScreenY() + dy;
                try {
                    mouse.click(cx, cy);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                return StepResult.ok(step.stepOrder(), actionType(),
                        chosen.text() + " (随机 " + candidates.size() + "选1)", cx, cy,
                        System.currentTimeMillis() - start);
            }
            sleep(interval);
        }
        return StepResult.fail(step.stepOrder(), actionType(), target,
                "未找到带延时的节点列表（前缀: " + target + "）", System.currentTimeMillis() - start);
    }
}
