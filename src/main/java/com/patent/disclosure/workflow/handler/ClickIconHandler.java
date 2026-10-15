package com.patent.disclosure.workflow.handler;

import com.patent.disclosure.automation.MirrorRegionService;
import com.patent.disclosure.automation.MouseController;
import com.patent.disclosure.automation.ScreenCapture;
import com.patent.disclosure.image.ImageTemplateMatcher;
import com.patent.disclosure.image.TemplateStore;
import com.patent.disclosure.workflow.StepConfigs;
import com.patent.disclosure.workflow.StepHandler;
import com.patent.disclosure.workflow.StepResult;
import com.patent.disclosure.workflow.WorkflowContext;
import com.patent.disclosure.workflow.WorkflowStep;
import org.springframework.stereotype.Component;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;

/**
 * ICON：按图标模板（templates/ 目录）匹配并点击。
 *
 * 解决"只有图标没有名字"的 App/按钮。
 *   targetValue    = 模板名（已在模板库上传）
 *   config.threshold = 相似度阈值（0~1，默认 0.85，越小越易命中）
 *   config.click     = false 时只查找返回坐标，不点击（默认点击）
 *   config.dx/dy     = 命中后偏移
 *   config.region    = 指定识别区域
 */
@Component
public class ClickIconHandler extends BaseOcrHandler implements StepHandler {

    private final TemplateStore templateStore;
    private final MouseController mouse;

    public ClickIconHandler(com.patent.disclosure.ocr.OcrService ocr,
                            ScreenCapture screenCapture,
                            MirrorRegionService regionService,
                            TemplateStore templateStore,
                            MouseController mouse) {
        super(ocr, screenCapture, regionService);
        this.templateStore = templateStore;
        this.mouse = mouse;
    }

    @Override
    public String actionType() {
        return "ICON";
    }

    @Override
    public StepResult execute(WorkflowStep step, String target, WorkflowContext ctx, long start) {
        int retries = step.retryCount() == null ? 3 : step.retryCount();
        long interval = step.timeout() == null ? 500 : step.timeout();
        int dx = StepConfigs.configInt(step, "dx", 0);
        int dy = StepConfigs.configInt(step, "dy", 0);
        double threshold = StepConfigs.configInt(step, "threshold", 85) / 100.0;
        boolean click = !"false".equalsIgnoreCase(StepConfigs.configStr(step, "click", "true"));
        String regionName = StepConfigs.configStr(step, "region", null);

        if (target == null || target.isBlank()) {
            return StepResult.fail(step.stepOrder(), step.actionType(), target,
                    "ICON 需要模板名（先在模板库上传）", System.currentTimeMillis() - start);
        }
        BufferedImage template = templateStore.load(target);
        if (template == null) {
            return StepResult.fail(step.stepOrder(), step.actionType(), target,
                    "模板不存在: " + target + "（请先上传）", System.currentTimeMillis() - start);
        }

        try {
            for (int i = 0; i <= retries; i++) {
                Rectangle region = regionService.getRegion(regionName);
                BufferedImage scene = screenCapture.capture(region);
                ImageTemplateMatcher.Match m = ImageTemplateMatcher.match(scene, template, threshold);
                if (m != null) {
                    int sx = region.x + m.centerX() + dx;
                    int sy = region.y + m.centerY() + dy;
                    if (click) {
                        mouse.click(sx, sy);
                    }
                    return StepResult.ok(step.stepOrder(), step.actionType(), target,
                            sx, sy, System.currentTimeMillis() - start);
                }
                sleep(interval);
            }
            return StepResult.fail(step.stepOrder(), step.actionType(), target,
                    "未匹配到图标模板: " + target + "（阈值 " + threshold + "）",
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
