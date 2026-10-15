package com.patent.disclosure.workflow.handler;

import com.patent.disclosure.automation.MouseController;
import org.springframework.stereotype.Component;

/** DOUBLE_CLICK：按文字/坐标定位后双击（定位逻辑复用 ClickHandler）。 */
@Component
public class DoubleClickHandler extends ClickHandler {

    public DoubleClickHandler(com.patent.disclosure.ocr.OcrService ocr,
                              com.patent.disclosure.automation.ScreenCapture screenCapture,
                              com.patent.disclosure.automation.MirrorRegionService regionService,
                              MouseController mouse) {
        super(ocr, screenCapture, regionService, mouse);
    }

    @Override
    public String actionType() {
        return "DOUBLE_CLICK";
    }

    @Override
    protected void doClick(int x, int y) throws Exception {
        mouse.doubleClick(x, y);
    }
}
