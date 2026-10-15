package com.patent.disclosure.controller;

import com.patent.disclosure.automation.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/desktop")
public class DesktopController {
    private final DesktopDiagnosticsService diagnostics;
    private final ScreenCapture screenCapture;
    private final MouseController mouse;
    private final KeyboardController keyboard;

    public DesktopController(DesktopDiagnosticsService diagnostics,
                             ScreenCapture screenCapture,
                             MouseController mouse,
                             KeyboardController keyboard) {
        this.diagnostics = diagnostics;
        this.screenCapture = screenCapture;
        this.mouse = mouse;
        this.keyboard = keyboard;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return diagnostics.diagnostics();
    }

    @GetMapping("/diagnostics")
    public Map<String, Object> diagnostics() {
        return diagnostics.diagnostics();
    }

    @PostMapping(value = "/screenshot", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] screenshot() throws IOException, AWTException {
        BufferedImage image = screenCapture.capture();
        return screenCapture.png(image);
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

    @PostMapping("/mouse/drag")
    public Map<String, Object> drag(@RequestBody DragRequest request) throws AWTException {
        mouse.drag(request.x1(), request.y1(), request.x2(), request.y2(), request.durationMs());
        return Map.of("success", true);
    }

    @PostMapping("/keyboard/type")
    public Map<String, Object> type(@RequestBody TextRequest request) throws AWTException {
        keyboard.type(request.text());
        return Map.of("success", true);
    }

    @PostMapping("/keyboard/key")
    public Map<String, Object> key(@RequestBody KeyRequest request) throws AWTException {
        keyboard.pressKey(request.key());
        return Map.of("success", true);
    }

    public record PointRequest(int x, int y) {}
    public record TextRequest(String text) {}
    public record DragRequest(int x1, int y1, int x2, int y2, long durationMs) {}
    public record KeyRequest(String key) {}
}
