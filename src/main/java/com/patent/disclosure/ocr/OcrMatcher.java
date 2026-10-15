package com.patent.disclosure.ocr;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * OCR 匹配工具：精确匹配优先，找不到再退化为包含匹配。
 * 避免标题/导航栏等长文本行抢走按钮的点击目标。
 */
public final class OcrMatcher {
    private OcrMatcher() {}

    /** 取第 index 个匹配（默认 0 = 第一个；负数从尾部数，-1 = 最后一个）。 */
    public static Optional<OcrElement> matchAt(List<OcrElement> elements, String target, int index) {
        if (target == null) return Optional.empty();
        String norm = normalize(target);
        if (norm.isEmpty()) return Optional.empty();

        List<OcrElement> exact = new ArrayList<>();
        List<OcrElement> contains = new ArrayList<>();
        for (OcrElement e : elements) {
            String n = normalize(e.text());
            if (n.isEmpty()) continue;
            if (n.equals(norm)) exact.add(e);
            else if (n.contains(norm)) contains.add(e);
        }

        List<OcrElement> pool = exact.isEmpty() ? contains : exact;
        if (pool.isEmpty()) return Optional.empty();
        int idx = index < 0 ? pool.size() + index : index;
        if (idx < 0 || idx >= pool.size()) return Optional.empty();
        return Optional.of(pool.get(idx));
    }

    public static Optional<OcrElement> match(List<OcrElement> elements, String target) {
        return matchAt(elements, target, 0);
    }

    public static String normalize(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase();
    }
}
