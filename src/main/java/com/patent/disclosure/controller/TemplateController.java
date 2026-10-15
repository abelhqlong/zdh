package com.patent.disclosure.controller;

import com.patent.disclosure.image.TemplateStore;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 图标模板管理（供 ICON 步骤使用）。
 *   GET    /api/template/list           全部模板名
 *   POST   /api/template/upload         multipart: name + file
 *   GET    /api/template/preview/{name} 返回 PNG 预览
 *   DELETE /api/template/{name}         删除
 */
@RestController
@RequestMapping("/api/template")
public class TemplateController {

    private final TemplateStore store;

    public TemplateController(TemplateStore store) {
        this.store = store;
    }

    @GetMapping("/list")
    public List<String> list() {
        return store.list();
    }

    @PostMapping("/upload")
    public Map<String, Object> upload(@RequestParam("name") String name,
                                      @RequestParam("file") MultipartFile file) throws IOException {
        String saved = store.save(name, file.getBytes());
        return Map.of("success", true, "name", saved);
    }

    @GetMapping(value = "/preview/{name}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> preview(@PathVariable String name) {
        byte[] bytes = store.raw(name);
        if (bytes == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes);
    }

    @DeleteMapping("/{name}")
    public Map<String, Object> delete(@PathVariable String name) {
        boolean removed = store.delete(name);
        return Map.of("success", removed, "name", name);
    }
}
