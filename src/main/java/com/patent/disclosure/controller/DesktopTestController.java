package com.patent.disclosure.controller;

import com.patent.disclosure.automation.KeyboardController;
import com.patent.disclosure.automation.MouseController;
import com.patent.disclosure.automation.ScreenCapture;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/desktop/test")
public class DesktopTestController {

    private final MouseController mouse;
    private final KeyboardController keyboard;
    private final ScreenCapture screenCapture;

    public DesktopTestController(MouseController mouse,
                                 KeyboardController keyboard,
                                 ScreenCapture screenCapture) {
        this.mouse = mouse;
        this.keyboard = keyboard;
        this.screenCapture = screenCapture;
    }

    @GetMapping("/screen")
    public Map<String, Object> screenInfo() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("headless", GraphicsEnvironment.isHeadless());

        if (GraphicsEnvironment.isHeadless()) {
            result.put("screenCount", 0);
            result.put("message", "当前 JVM 是 headless，无法读取屏幕");
            return result;
        }

        try {
            GraphicsDevice[] devices =
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
            result.put("screenCount", devices.length);

            var screens = new java.util.ArrayList<Map<String, Integer>>();
            for (int i = 0; i < devices.length; i++) {
                Rectangle r = devices[i].getDefaultConfiguration().getBounds();
                Map<String, Integer> s = new LinkedHashMap<>();
                s.put("index", i);
                s.put("x", r.x);
                s.put("y", r.y);
                s.put("width", r.width);
                s.put("height", r.height);
                screens.add(s);
            }
            result.put("screens", screens);
            result.put("message", "屏幕信息读取成功");
        } catch (Throwable t) {
            result.put("success", false);
            result.put("error", t.getClass().getName());
            result.put("message", String.valueOf(t.getMessage()));
        }
        return result;
    }

    @PostMapping("/mouse/move-center")
    public Map<String, Object> moveCenter() throws AWTException {
        Rectangle r = screenCapture.virtualScreenBounds();
        int x = r.x + r.width / 2;
        int y = r.y + r.height / 2;
        mouse.move(x, y);
        return Map.of("success", true, "x", x, "y", y, "message", "鼠标已移动到虚拟屏幕中心");
    }

    @PostMapping("/mouse/move")
    public Map<String, Object> move(@RequestBody PointRequest request) throws AWTException {
        mouse.move(request.x(), request.y());
        return Map.of("success", true, "x", request.x(), "y", request.y());
    }

    @PostMapping("/mouse/click")
    public Map<String, Object> click(@RequestBody PointRequest request) throws AWTException {
        mouse.click(request.x(), request.y());
        return Map.of("success", true, "x", request.x(), "y", request.y());
    }

    @PostMapping("/keyboard/type")
    public Map<String, Object> type(@RequestBody TextRequest request) throws AWTException {
        keyboard.type(request.text());
        return Map.of("success", true, "length", request.text() == null ? 0 : request.text().length());
    }

    @PostMapping(value = "/screenshot", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] screenshot() throws Exception {
        return screenCapture.png(screenCapture.capture());
    }

    @PostMapping("/all")
    public Map<String, Object> all(@RequestBody AllTestRequest request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("headless", GraphicsEnvironment.isHeadless());

        if (GraphicsEnvironment.isHeadless()) {
            result.put("success", false);
            result.put("message", "当前 JVM 是 headless，无法执行鼠标/键盘/截屏测试");
            return result;
        }

        try {
            Rectangle r = screenCapture.virtualScreenBounds();
            int x = request.x() == null ? r.x + r.width / 2 : request.x();
            int y = request.y() == null ? r.y + r.height / 2 : request.y();

            mouse.move(x, y);
            Thread.sleep(500);

            if (Boolean.TRUE.equals(request.click())) {
                mouse.click(x, y);
                Thread.sleep(300);
            }

            if (request.text() != null && !request.text().isEmpty()) {
                keyboard.type(request.text());
            }

            // Capture after the interaction; returned as status rather than image.
            screenCapture.capture();

            result.put("success", true);
            result.put("x", x);
            result.put("y", y);
            result.put("clicked", Boolean.TRUE.equals(request.click()));
            result.put("typed", request.text() != null && !request.text().isEmpty());
            result.put("screenshot", true);
            result.put("message", "桌面自动化综合测试完成");
            return result;
        } catch (Throwable t) {
            result.put("success", false);
            result.put("error", t.getClass().getName());
            result.put("message", String.valueOf(t.getMessage()));
            return result;
        }
    }

    public record PointRequest(int x, int y) {}
    public record TextRequest(String text) {}
    public record AllTestRequest(Integer x, Integer y, Boolean click, String text) {}
}
