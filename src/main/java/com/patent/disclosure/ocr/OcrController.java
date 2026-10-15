package com.patent.disclosure.ocr;

import com.patent.disclosure.automation.MirrorRegionService;
import com.patent.disclosure.automation.MouseController;
import com.patent.disclosure.automation.ScreenCapture;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ocr")
public class OcrController {
    private final OcrService ocr;
    private final MirrorRegionService regionService;
    private final ScreenCapture screenCapture;
    private final MouseController mouse;

    public OcrController(OcrService ocr,
                         MirrorRegionService regionService,
                         ScreenCapture screenCapture,
                         MouseController mouse) {
        this.ocr = ocr;
        this.regionService = regionService;
        this.screenCapture = screenCapture;
        this.mouse = mouse;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>(ocr.status());
        result.put("region", region());
        return result;
    }

    @PostMapping(value = "/region", produces = MediaType.APPLICATION_JSON_VALUE)
    public OcrResult recognizeRegion() throws Exception {
        Rectangle region = regionService.getRegion();
        return ocr.recognize(screenCapture.capture(region), region);
    }

    @PostMapping(value = "/screen", produces = MediaType.APPLICATION_JSON_VALUE)
    public OcrResult recognizeScreen() throws Exception {
        Rectangle region = screenCapture.virtualScreenBounds();
        return ocr.recognize(screenCapture.capture(region), region);
    }

    @PostMapping("/find")
    public Map<String, Object> find(@RequestBody FindRequest request) throws Exception {
        if (request.text() == null || request.text().isBlank()) {
            throw new IllegalArgumentException("text 不能为空");
        }
        Rectangle region = regionService.getRegion();
        OcrResult result = ocr.recognize(screenCapture.capture(region), region);
        String norm = OcrMatcher.normalize(request.text());
        List<OcrElement> matches = result.elements().stream()
                .filter(e -> {
                    String n = OcrMatcher.normalize(e.text());
                    return n.equals(norm) || n.contains(norm);
                })
                .toList();
        return Map.of(
                "success", result.success() && !matches.isEmpty(),
                "target", request.text(),
                "matches", matches,
                "ocr", result
        );
    }

    @PostMapping("/click")
    public Map<String, Object> click(@RequestBody ClickRequest request) throws Exception {
        if (request.text() == null || request.text().isBlank()) {
            throw new IllegalArgumentException("text 不能为空");
        }
        Rectangle region = regionService.getRegion();
        OcrResult result = ocr.recognize(screenCapture.capture(region), region);
        OcrElement match = OcrMatcher.matchAt(result.elements(), request.text(),
                request.index() == null ? 0 : request.index()).orElse(null);
        if (match == null) {
            return Map.of("success", false, "target", request.text(), "message", "OCR 未找到目标文字", "ocr", result);
        }
        int x = match.centerScreenX();
        int y = match.centerScreenY();
        mouse.click(x, y);
        return Map.of("success", true, "target", request.text(), "x", x, "y", y, "element", match);
    }

    private Map<String, Integer> region() {
        Rectangle r = regionService.getRegion();
        return Map.of("x", r.x, "y", r.y, "width", r.width, "height", r.height);
    }

    public record FindRequest(String text) {}
    public record ClickRequest(String text, Integer index) {}
}
