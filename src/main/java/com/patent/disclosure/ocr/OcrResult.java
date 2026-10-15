package com.patent.disclosure.ocr;

import java.util.List;

public record OcrResult(
        boolean success,
        String engine,
        String language,
        String text,
        List<OcrElement> elements,
        long elapsedMs,
        String message
) {}
