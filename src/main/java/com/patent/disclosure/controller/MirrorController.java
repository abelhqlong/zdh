package com.patent.disclosure.controller;

import com.patent.disclosure.automation.MirrorRegionService;
import com.patent.disclosure.automation.ScreenCapture;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/mirror")
public class MirrorController {
    private final MirrorRegionService regionService;
    private final ScreenCapture screenCapture;

    public MirrorController(MirrorRegionService regionService, ScreenCapture screenCapture) {
        this.regionService = regionService;
        this.screenCapture = screenCapture;
    }

    @GetMapping("/region")
    public Map<String, Integer> getRegion() {
        Rectangle r = regionService.getRegion();
        return Map.of("x", r.x, "y", r.y, "width", r.width, "height", r.height);
    }

    @PostMapping("/region")
    public Map<String, Integer> setRegion(@RequestBody RegionRequest request) {
        regionService.setRegion(request.x(), request.y(), request.width(), request.height());
        return getRegion();
    }

    @DeleteMapping("/region")
    public Map<String, Object> clearRegion() {
        regionService.clear();
        return Map.of("success", true);
    }

    @PostMapping(value = "/screenshot", produces = MediaType.IMAGE_PNG_VALUE)
    public byte[] screenshot() throws IOException, AWTException {
        Rectangle r = regionService.getRegion();
        BufferedImage image = screenCapture.capture(r);
        return screenCapture.png(image);
    }

    // ---------- L2 命名区域 ----------
    @GetMapping("/regions")
    public Map<String, Map<String, Integer>> listRegions() {
        Map<String, Map<String, Integer>> out = new java.util.TreeMap<>();
        regionService.listRegions().forEach((name, r) ->
                out.put(name, Map.of("x", r.x, "y", r.y, "width", r.width, "height", r.height)));
        return out;
    }

    @PostMapping("/region/named")
    public Map<String, Object> saveNamedRegion(@RequestBody NamedRegionRequest request) {
        regionService.setNamedRegion(request.name(), request.x(), request.y(), request.width(), request.height());
        Rectangle r = regionService.getRegion(request.name());
        return Map.of("success", true, "name", request.name(),
                "region", Map.of("x", r.x, "y", r.y, "width", r.width, "height", r.height));
    }

    @GetMapping("/region/named/{name}")
    public Map<String, Integer> getNamedRegion(@PathVariable String name) {
        Rectangle r = regionService.getRegion(name);
        return Map.of("x", r.x, "y", r.y, "width", r.width, "height", r.height);
    }

    @DeleteMapping("/region/named/{name}")
    public Map<String, Object> deleteNamedRegion(@PathVariable String name) {
        regionService.deleteNamedRegion(name);
        return Map.of("success", true, "name", name);
    }

    // 一键切换到平板iPad的识别区域
    @GetMapping("/tablet")
    public Map<String, Integer> switchToTablet() {
        regionService.setRegion(1175, 59, 711, 1021);
        return getRegion();
    }

    // 一键切换到手机的识别区域
    @GetMapping("/phone")
    public Map<String, Integer> switchToPhone() {
        regionService.setRegion(1505, 0, 415, 1080);
        return getRegion();
    }

    public record RegionRequest(int x, int y, int width, int height) {}
    public record NamedRegionRequest(String name, int x, int y, int width, int height) {}
}
