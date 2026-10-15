package com.patent.disclosure.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 环境设置持久化（产品化）：默认 Excel 路径 / OCR 引擎偏好 / 节点 / AMG 开关。
 * 存本地 settings.json；OCR 引擎等静态配置项切换后需重启后端生效（提示由前端给出）。
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final File SETTINGS_FILE = new File(System.getProperty("user.dir"), "settings.json");

    private static Map<String, Object> defaults() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("excelPath", "");
        m.put("ocrEngine", "baidu");       // baidu | tesseract | paddleocr(未内置,仅存偏好)
        m.put("node", "MIYA- 自动");
        m.put("amgEnabled", true);
        m.put("region", Map.of("x", 1175, "y", 59, "width", 711, "height", 1021));
        return m;
    }

    @GetMapping
    public Map<String, Object> get() {
        Map<String, Object> base = defaults();
        if (SETTINGS_FILE.exists()) {
            try {
                Map<String, Object> saved = MAPPER.readValue(SETTINGS_FILE, new TypeReference<Map<String, Object>>() {});
                base.putAll(saved);
            } catch (Exception e) {
                base.put("readError", e.getMessage());
            }
        }
        return base;
    }

    @PostMapping
    public Map<String, Object> save(@RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> saved = MAPPER.readValue(SETTINGS_FILE.exists() ? SETTINGS_FILE : new File("__none__"),
                    new TypeReference<Map<String, Object>>() {});
            saved.putAll(body);
            MAPPER.writeValue(SETTINGS_FILE, saved);
            return Map.of("success", true, "path", SETTINGS_FILE.getAbsolutePath());
        } catch (Exception e) {
            // 文件不存在等场景, 直接全新写
            try {
                MAPPER.writeValue(SETTINGS_FILE, body);
                return Map.of("success", true, "path", SETTINGS_FILE.getAbsolutePath());
            } catch (Exception e2) {
                throw new RuntimeException("保存设置失败: " + e2.getMessage(), e2);
            }
        }
    }
}
