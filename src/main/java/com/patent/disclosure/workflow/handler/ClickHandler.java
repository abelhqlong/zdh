package com.patent.disclosure.workflow.handler;

import com.patent.disclosure.automation.MirrorRegionService;
import com.patent.disclosure.automation.MouseController;
import com.patent.disclosure.automation.ScreenCapture;
import com.patent.disclosure.ocr.OcrCacheService;
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
 * CLICK：按文字 OCR 查找并点击，或按 x/y 直接点击。
 *
 * 扩展（L2）：
 *   config.dx / config.dy  - 相对文字中心偏移后点击（解决文字与按钮错位）
 *   config.region          - 指定命名识别区域
 *   config.index           - 第几个匹配（0=第一个，-1=最后一个）
 * 子类可覆盖 doClick() 实现双击/右键等（DOUBLE_CLICK / RIGHT_CLICK）。
 */
@Component
public class ClickHandler extends BaseOcrHandler implements StepHandler {

    protected final MouseController mouse;
    protected final OcrCacheService ocrCache;

    public ClickHandler(OcrService ocr, ScreenCapture screenCapture,
                        MirrorRegionService regionService, MouseController mouse,
                        OcrCacheService ocrCache) {
        super(ocr, screenCapture, regionService);
        this.mouse = mouse;
        this.ocrCache = ocrCache;
    }

    @Override
    public String actionType() {
        return "CLICK";
    }

    @Override
    public StepResult execute(WorkflowStep step, String target, WorkflowContext ctx, long start) {
        int retries = step.retryCount() == null ? 3 : step.retryCount();
        long interval = step.timeout() == null ? 500 : step.timeout();
        int index = StepConfigs.configInt(step, "index", 0);
        int dx = StepConfigs.configInt(step, "dx", 0);
        int dy = StepConfigs.configInt(step, "dy", 0);
        try {
            // 直接坐标优先（偏移同样生效）
            if (step.x() != null && step.y() != null) {
                doClick(step.x() + dx, step.y() + dy);
                return StepResult.ok(step.stepOrder(), step.actionType(), target,
                        step.x() + dx, step.y() + dy, System.currentTimeMillis() - start);
            }
            if (target == null || target.isBlank()) {
                return StepResult.fail(step.stepOrder(), step.actionType(), target,
                        "CLICK 需要 x/y 坐标或目标文字", System.currentTimeMillis() - start);
            }

            // 先查缓存有没有坐标（如果配置了 noCache=true，就跳过缓存）
            Object noCacheObj = step.config() == null ? null : step.config().get("noCache");
            boolean noCache = noCacheObj instanceof Boolean b ? b : "true".equals(String.valueOf(noCacheObj));
            String cacheKey = "click_" + target;
            if (!noCache) {
                int[] cachedPos = ocrCache.get(cacheKey);
                if (cachedPos != null) {
                    int cx = cachedPos[0] + dx;
                    int cy = cachedPos[1] + dy;
                    doClick(cx, cy);
                    // 缓存模式下点击很快，加个等待时间让页面有时间加载响应（模拟原来 OCR 的耗时）
                    sleep(3000);
                    return StepResult.ok(step.stepOrder(), step.actionType(), target,
                            cx, cy, System.currentTimeMillis() - start);
                }
            }

            String lastOcr = null;
            for (int i = 0; i <= retries; i++) {
                OcrResult result = recognizeRegion(step);
                lastOcr = result.text();
                Optional<OcrElement> m = OcrMatcher.matchAt(result.elements(), target, index);
                if (m.isPresent()) {
                    OcrElement e = m.get();
                    int cx = e.centerScreenX() + dx, cy = e.centerScreenY() + dy;
                    doClick(cx, cy);
                    // 把坐标存进缓存，下次直接用（noCache 的时候不存）
                    if (!noCache) {
                        ocrCache.put(cacheKey, e.centerScreenX(), e.centerScreenY());
                    }
                    return StepResult.ok(step.stepOrder(), step.actionType(), target,
                            cx, cy, System.currentTimeMillis() - start);
                }
                sleep(interval);
            }
            return StepResult.failWithOcr(step.stepOrder(), step.actionType(), target,
                    "未找到可点击文字: " + target, lastOcr, System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 点击动作钩子：子类可覆盖为双击/右键 */
    protected void doClick(int x, int y) throws Exception {
        mouse.click(x, y);
    }
}
