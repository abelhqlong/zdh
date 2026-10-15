package com.patent.disclosure.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 自定义流程存储：以 JSON 文件保存到 flows/ 目录（默认相对运行目录），
 * 不依赖数据库，其他电脑拷贝 flows/ 目录即可带过去。
 */
@Service
public class CustomFlowService {
    private final Path dir;
    private final ObjectMapper objectMapper;

    public CustomFlowService(@Value("${automation.flows-dir:flows}") String dir, ObjectMapper objectMapper) {
        this.dir = Paths.get(dir).toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    private Path file(String name) {
        String safe = name.replaceAll("[^a-zA-Z0-9_-]", "_");
        return dir.resolve(safe + ".json");
    }

    /** 已保存的自定义流程名列表 */
    public List<String> list() {
        if (!Files.isDirectory(dir)) return List.of();
        try (var stream = Files.list(dir)) {
            return stream.filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replaceAll("\\.json$", ""))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    /** 读取自定义流程，不存在返回 null */
    public WorkflowDefinition get(String name) {
        try {
            Path f = file(name);
            if (!Files.exists(f)) return null;
            return objectMapper.readValue(Files.readString(f, StandardCharsets.UTF_8), WorkflowDefinition.class);
        } catch (IOException e) {
            throw new IllegalStateException("读取自定义流程失败: " + e.getMessage());
        }
    }

    public void save(WorkflowDefinition definition) {
        try {
            Files.createDirectories(dir);
            Path f = file(definition.name());
            Files.writeString(f,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(definition),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("保存自定义流程失败: " + e.getMessage());
        }
    }

    public boolean delete(String name) {
        try {
            return Files.deleteIfExists(file(name));
        } catch (IOException e) {
            throw new IllegalStateException("删除自定义流程失败: " + e.getMessage());
        }
    }
}
