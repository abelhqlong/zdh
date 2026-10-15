package com.patent.disclosure.image;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图标模板库：以 PNG/JPG 文件存到 templates/ 目录（默认相对运行目录），
 * 供 ICON 步骤按名字读取匹配。支持内存缓存，更新/删除时失效。
 */
@Service
public class TemplateStore {
    private final Path dir;
    private final Map<String, BufferedImage> cache = new ConcurrentHashMap<>();

    public TemplateStore(@Value("${automation.templates-dir:templates}") String dir) {
        this.dir = Paths.get(dir).toAbsolutePath().normalize();
    }

    private Path file(String name) {
        String safe = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        return dir.resolve(safe + ".png");
    }

    /** 全部模板名 */
    public List<String> list() {
        if (!Files.isDirectory(dir)) return List.of();
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.toString().toLowerCase().endsWith(".png"))
                    .map(p -> p.getFileName().toString().replaceAll("\\.png$", ""))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** 保存模板（覆盖同名），成功返回模板名 */
    public String save(String name, byte[] bytes) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("模板名不能为空");
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) throw new IllegalArgumentException("无法解析图片（支持 PNG/JPG）");
            Files.createDirectories(dir);
            Path f = file(name);
            ImageIO.write(img, "png", f.toFile());
            cache.put(name.trim(), img);
            return name.trim();
        } catch (IOException e) {
            throw new IllegalStateException("保存模板失败: " + e.getMessage());
        }
    }

    /** 读取模板（带缓存）；不存在返回 null */
    public BufferedImage load(String name) {
        if (name == null || name.isBlank()) return null;
        String key = name.trim();
        BufferedImage cached = cache.get(key);
        if (cached != null) return cached;
        Path f = file(key);
        if (!Files.exists(f)) return null;
        try {
            BufferedImage img = ImageIO.read(f.toFile());
            if (img != null) cache.put(key, img);
            return img;
        } catch (IOException e) {
            return null;
        }
    }

    /** 读取模板原始字节（供预览） */
    public byte[] raw(String name) {
        Path f = file(name == null ? "" : name.trim());
        if (!Files.exists(f)) return null;
        try {
            return Files.readAllBytes(f);
        } catch (IOException e) {
            return null;
        }
    }

    public boolean delete(String name) {
        cache.remove(name == null ? "" : name.trim());
        try {
            return Files.deleteIfExists(file(name == null ? "" : name.trim()));
        } catch (IOException e) {
            throw new IllegalStateException("删除模板失败: " + e.getMessage());
        }
    }
}
