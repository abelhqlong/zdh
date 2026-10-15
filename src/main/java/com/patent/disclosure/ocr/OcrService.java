package com.patent.disclosure.ocr;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Map;

public interface OcrService {
    OcrResult recognize(BufferedImage image, Rectangle screenRegion);

    default OcrResult recognize(BufferedImage image) {
        return recognize(image, new Rectangle(0, 0, image.getWidth(), image.getHeight()));
    }

    /** 引擎名称（tesseract / baidu 等），用于日志与状态展示。 */
    default String engineName() {
        return "unknown";
    }

    /** 引擎状态详情，供 /api/ocr/status 展示。 */
    default Map<String, Object> status() {
        return Map.of("engine", engineName());
    }
}
