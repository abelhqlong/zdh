package com.patent.disclosure.workflow.handler;

import com.patent.disclosure.automation.MirrorRegionService;
import com.patent.disclosure.automation.ScreenCapture;
import com.patent.disclosure.ocr.OcrResult;
import com.patent.disclosure.ocr.OcrService;
import com.patent.disclosure.workflow.StepConfigs;
import com.patent.disclosure.workflow.WorkflowStep;

import java.awt.Rectangle;

/** 需要 OCR 识别的步骤执行器公共基类：统一注入并封装"区域截图 + 识别"。 */
public abstract class BaseOcrHandler {
    protected final OcrService ocr;
    protected final ScreenCapture screenCapture;
    protected final MirrorRegionService regionService;

    /** OCR 调用次数计数器 */
    private static int ocrCallCount = 0;

    public static int getOcrCallCount() {
        return ocrCallCount;
    }

    public static void resetOcrCallCount() {
        ocrCallCount = 0;
    }

    protected BaseOcrHandler(OcrService ocr,
                             ScreenCapture screenCapture,
                             MirrorRegionService regionService) {
        this.ocr = ocr;
        this.screenCapture = screenCapture;
        this.regionService = regionService;
    }

    /** 识别当前默认区域 */
    protected OcrResult recognizeRegion() {
        return recognizeRegion((WorkflowStep) null);
    }

    /** 对指定区域（config.region，缺省=默认区域）截图并识别 */
    protected OcrResult recognizeRegion(WorkflowStep step) {
        String regionName = StepConfigs.configStr(step, "region", null);
        return recognizeRegion(regionName);
    }

    protected OcrResult recognizeRegion(String regionName) {
        Rectangle region = regionService.getRegion(regionName);
        ocrCallCount++; // 统计 OCR 调用次数
        try {
            return ocr.recognize(screenCapture.capture(region), region);
        } catch (Exception e) {
            return new OcrResult(false, ocr.engineName(), "", "",
                    java.util.List.of(), 0, "截图失败: " + e.getMessage());
        }
    }

    protected void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
