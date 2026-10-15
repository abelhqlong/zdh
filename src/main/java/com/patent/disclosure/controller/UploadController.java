package com.patent.disclosure.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Excel 文件上传（产品化：数据源通过上传选择，替代手动输入路径）。
 * 上传文件保存到 uploads/ 目录，返回绝对路径供流程/批量/单条使用。
 */
@RestController
@RequestMapping("/api/upload")
public class UploadController {

    private static final File UPLOAD_DIR = new File(System.getProperty("user.dir"), "uploads");

    @PostMapping("/excel")
    public Map<String, Object> uploadExcel(@RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }
        String name = file.getOriginalFilename();
        String ext = "";
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0) {
                ext = name.substring(dot).toLowerCase();
            }
        }
        if (!ext.equals(".xls") && !ext.equals(".xlsx") && !ext.equals(".csv")) {
            throw new IllegalArgumentException("仅支持 .xls / .xlsx / .csv 文件");
        }
        if (!UPLOAD_DIR.exists() && !UPLOAD_DIR.mkdirs()) {
            throw new IOException("无法创建上传目录: " + UPLOAD_DIR.getAbsolutePath());
        }
        String safeName = (name == null ? "upload" : name).replaceAll("[\\\\/:*?\"<>|]", "_");
        File dest = new File(UPLOAD_DIR, System.currentTimeMillis() + "_" + safeName);
        file.transferTo(dest);
        return Map.of("success", true, "path", dest.getAbsolutePath(), "name", name);
    }

    /** 列出 uploads 目录里已上传的 Excel 文件, 按修改时间倒序 */
    @GetMapping("/list")
    public Map<String, Object> list() {
        List<Map<String, Object>> files = new ArrayList<>();
        if (UPLOAD_DIR.exists()) {
            File[] arr = UPLOAD_DIR.listFiles();
            if (arr != null) {
                for (File f : arr) {
                    String name = f.getName();
                    // 过滤 Excel 临时锁定文件（~$ 开头）
                    if (name.startsWith("~$")) continue;
                    if (f.isFile() && (name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".csv"))) {
                        files.add(Map.of(
                            "name", name,
                            "path", f.getAbsolutePath(),
                            "size", f.length(),
                            "modified", f.lastModified()
                        ));
                    }
                }
                files.sort(Comparator.comparingLong((Map<String, Object> m) -> (Long) m.get("modified")).reversed());
            }
        }
        return Map.of("files", files);
    }

    /** 获取上次上传的 Excel（最新的那个） */
    @GetMapping("/last")
    public Map<String, Object> last() {
        List<Map<String, Object>> files = (List<Map<String, Object>>) list().get("files");
        if (files == null || files.isEmpty()) {
            return Map.of("hasLast", false);
        }
        Map<String, Object> latest = files.get(0);
        return Map.of("hasLast", true, "path", latest.get("path"), "name", latest.get("name"));
    }
}
