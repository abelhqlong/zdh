package com.patent.disclosure.ocr;

public record OcrElement(
        String text,
        int x,
        int y,
        int width,
        int height,
        int screenX,
        int screenY,
        int screenWidth,
        int screenHeight,
        double confidence
) {
    public int centerScreenX() { return screenX + screenWidth / 2; }
    public int centerScreenY() { return screenY + screenHeight / 2; }
}
