package com.patent.disclosure.workflow.handler;

import com.patent.disclosure.automation.MouseController;
import com.patent.disclosure.ocr.OcrCacheService;
import org.springframework.stereotype.Component;

/** RIGHT_CLICK：按文字/坐标定位后右键（定位逻辑复用 ClickHandler）。 */
@Component
public class RightClickHandler extends ClickHandler {

    public RightClickHandler(com.patent.disclosure.ocr.OcrService ocr,
                             com.patent.disclosure.automation.ScreenCapture screenCapture,
                             com.patent.disclosure.automation.MirrorRegionService regionService,
                             MouseController mouse,
                             OcrCacheService ocrCache) {
        super(ocr, screenCapture, regionService, mouse, ocrCache);
    }

    @Override
    public String actionType() {
        return "RIGHT_CLICK";
    }

    @Override
    protected void doClick(int x, int y) throws Exception {
        mouse.rightClick(x, y);
    }
}
