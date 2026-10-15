package com.patent.disclosure.workflow;

public record StepResult(
        int stepOrder,
        String actionType,
        String targetValue,
        boolean success,
        String status,
        String message,
        String ocrText,
        Integer x,
        Integer y,
        long elapsedMs
) {
    public static StepResult ok(int order, String action, String target, Integer x, Integer y, long elapsed) {
        return new StepResult(order, action, target, true, "OK", "成功", null, x, y, elapsed);
    }

    public static StepResult waiting(int order, String action, String target, String message, long elapsed) {
        return new StepResult(order, action, target, true, "WAITING_USER", message, null, null, null, elapsed);
    }

    public static StepResult fail(int order, String action, String target, String message, long elapsed) {
        return new StepResult(order, action, target, false, "FAILED", message, null, null, null, elapsed);
    }

    /** 失败并附带最后一次 OCR 识别到的屏幕文字（便于流程修改：对照实际文案改 targetValue） */
    public static StepResult failWithOcr(int order, String action, String target, String message, String ocrText, long elapsed) {
        return new StepResult(order, action, target, false, "FAILED", message, ocrText, null, null, elapsed);
    }
}
