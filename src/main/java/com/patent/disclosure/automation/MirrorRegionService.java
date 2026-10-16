package com.patent.disclosure.automation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.Rectangle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 爱思远控/投屏识别区域管理。
 *
 * L2 起支持多个命名区域：
 *   - 默认区域（"default"）兼容旧 API：getRegion()/setRegion()/clear()；
 *   - 步骤可用 config.region=区域名 指定用哪个区域截图识别（如手机画面、验证码弹窗、桌面区域）。
 *
 * 启动时从 application.yml 的 automation.mirror.* 读取固定投屏识别区域。
 */
@Service
public class MirrorRegionService {
    private final Map<String, Rectangle> regions = new ConcurrentHashMap<>();
    private volatile Rectangle defaultRegion;

    public MirrorRegionService(
            @Value("${automation.mirror.x:0}") int x,
            @Value("${automation.mirror.y:0}") int y,
            @Value("${automation.mirror.width:0}") int width,
            @Value("${automation.mirror.height:0}") int height) {
        if (width > 0 && height > 0) {
            this.defaultRegion = new Rectangle(x, y, width, height);
        }
    }

    /** 默认区域（未设置时回退到虚拟屏幕） */
    public Rectangle getRegion() {
        Rectangle r = defaultRegion;
        if (r != null) return new Rectangle(r);
        try {
            return new Rectangle(new ScreenCapture().virtualScreenBounds());
        } catch (Throwable ignored) {
            return new Rectangle(0, 0, 1, 1);
        }
    }

    /** 按名称取区域；null/空/缺省 = 默认区域 */
    public Rectangle getRegion(String name) {
        if (name == null || name.isBlank() || "default".equalsIgnoreCase(name)) return getRegion();
        Rectangle r = regions.get(name.trim().toLowerCase());
        return r == null ? getRegion() : new Rectangle(r);
    }

    public void setRegion(int x, int y, int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("width/height must be > 0");
        this.defaultRegion = new Rectangle(x, y, width, height);
    }

    /** 保存命名区域（name=default 时覆盖默认区域） */
    public void setNamedRegion(String name, int x, int y, int width, int height) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("区域名不能为空");
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("width/height must be > 0");
        Rectangle rect = new Rectangle(x, y, width, height);
        String key = name.trim().toLowerCase();
        if ("default".equals(key)) {
            this.defaultRegion = rect;
        } else {
            regions.put(key, rect);
        }
    }

    public void deleteNamedRegion(String name) {
        if (name == null || name.isBlank()) return;
        regions.remove(name.trim().toLowerCase());
    }

    /** 全部区域（含 default 与命名区域） */
    public Map<String, Rectangle> listRegions() {
        Map<String, Rectangle> all = new ConcurrentHashMap<>();
        all.put("default", getRegion());
        regions.forEach(all::put);
        return all;
    }

    public void clear() {
        defaultRegion = null;
        regions.clear();
    }
}
