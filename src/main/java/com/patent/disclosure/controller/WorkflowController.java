package com.patent.disclosure.controller;

import com.patent.disclosure.service.BatchRegisterService;
import com.patent.disclosure.workflow.CustomFlowService;
import com.patent.disclosure.workflow.WorkflowDefinition;
import com.patent.disclosure.workflow.WorkflowEngine;
import com.patent.disclosure.workflow.WorkflowPresets;
import com.patent.disclosure.workflow.WorkflowReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/workflow")
public class WorkflowController {
    private static final Logger log = LoggerFactory.getLogger(WorkflowController.class);
    private final WorkflowEngine engine;
    private final CustomFlowService customFlow;
    private final BatchRegisterService batchRegisterService;

    public WorkflowController(WorkflowEngine engine, CustomFlowService customFlow, BatchRegisterService batchRegisterService) {
        this.engine = engine;
        this.customFlow = customFlow;
        this.batchRegisterService = batchRegisterService;
    }

    /** 预设流程列表 */
    @GetMapping("/presets")
    public Map<String, Object> presets() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, WorkflowDefinition> e : WorkflowPresets.all().entrySet()) {
            WorkflowDefinition d = e.getValue();
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", d.name());
            info.put("description", d.description());
            info.put("steps", d.steps().size());
            result.put(e.getKey(), info);
        }
        return result;
    }

    /** 运行内置预设流程，可带变量：{ "variables": { "APPLE_ID": "...", "PASSWORD": "..." } } */
    @PostMapping("/run-preset/{name}")
    public WorkflowReport runPreset(@PathVariable String name,
                                    @RequestBody(required = false) Map<String, Object> body) {
        WorkflowDefinition definition = WorkflowPresets.get(name);
        if (definition == null) {
            throw new IllegalArgumentException("未知预设流程: " + name + "，可用: " + WorkflowPresets.all().keySet());
        }
        Map<String, String> variables = extractVariables(body);
        return engine.execute(definition, variables);
    }

    /* ---------- 自定义流程（前端可视化配置，JSON 存 flows/ 目录） ---------- */

    /** 自定义流程列表 */
    @GetMapping("/custom")
    public Map<String, Object> customList() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : customFlow.list()) {
            WorkflowDefinition d = customFlow.get(name);
            Map<String, Object> info = new LinkedHashMap<>();
            if (d != null) {
                info.put("name", d.name());
                info.put("description", d.description());
                info.put("steps", d.steps().size());
            }
            result.put(name, info);
        }
        return result;
    }

    /** 读取自定义流程定义 */
    @GetMapping("/custom/{name}")
    public WorkflowDefinition customGet(@PathVariable String name) {
        WorkflowDefinition d = customFlow.get(name);
        if (d == null) throw new IllegalArgumentException("自定义流程不存在: " + name);
        return d;
    }

    /** 保存自定义流程（存在则覆盖） */
    @PostMapping("/custom")
    public Map<String, Object> customSave(@RequestBody WorkflowDefinition definition) {
        if (definition == null || definition.name() == null || definition.name().isBlank()) {
            throw new IllegalArgumentException("流程名不能为空");
        }
        if (definition.steps() == null || definition.steps().isEmpty()) {
            throw new IllegalArgumentException("至少需要一个步骤");
        }
        customFlow.save(definition);
        return Map.of("success", true, "name", definition.name(), "steps", definition.steps().size());
    }

    /** 删除自定义流程 */
    @DeleteMapping("/custom/{name}")
    public Map<String, Object> customDelete(@PathVariable String name) {
        boolean ok = customFlow.delete(name);
        return Map.of("success", ok, "name", name);
    }

    /** 运行自定义流程：body { variables?, startIndex?, endIndex?, progressFile? }
     *  progressFile: 相对工作目录的日志文件名；提供后每执行完一步实时追加一行进度(供前端轮询展示)。 */
    @PostMapping("/custom/{name}/run")
    public WorkflowReport customRun(@PathVariable String name,
                                    @RequestBody(required = false) Map<String, Object> body) {
        WorkflowDefinition d = customFlow.get(name);
        if (d == null) throw new IllegalArgumentException("自定义流程不存在: " + name);
        int startIndex = body == null ? 0 : (body.get("startIndex") == null ? 0 : Integer.parseInt(String.valueOf(body.get("startIndex"))));
        int endIndex = body == null ? Integer.MAX_VALUE : (body.get("endIndex") == null ? Integer.MAX_VALUE : Integer.parseInt(String.valueOf(body.get("endIndex"))));
        Map<String, String> vars = extractVariables(body);
        String progressFile = body == null ? null : (body.get("progressFile") == null ? null : String.valueOf(body.get("progressFile")));
        if (progressFile == null || progressFile.isBlank()) {
            return engine.execute(d, vars, startIndex, endIndex);
        }
        File workDir = new File(System.getProperty("user.dir"));
        File pf = new File(workDir, progressFile.replace("..", "_").replace('/', '_').replace('\\', '_'));
        WorkflowEngine.ProgressListener listener = (step, target, r) -> {
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(pf, true), StandardCharsets.UTF_8))) {
                String line = String.format("[步骤%03d] %s %s %s",
                        step.stepOrder(), step.actionType(),
                        target == null ? "" : target, r.success() ? "OK" : "FAIL");
                bw.write(line);
                bw.newLine();
            } catch (Exception ignored) {
            }
        };
        return engine.execute(d, vars, startIndex, endIndex, listener);
    }

    /** 运行自定义流程定义 */
    @PostMapping("/run")
    public WorkflowReport run(@RequestBody WorkflowDefinition definition) {
        if (definition == null || definition.steps() == null || definition.steps().isEmpty()) {
            throw new IllegalArgumentException("steps 不能为空");
        }
        return engine.execute(definition, Map.of());
    }

    /** 一键全流程：按顺序串联预设/自定义流程，任一失败即中断。
     *  body: { "flows": ["preflight","erase","activate","poshmark"], "variables": { "IP": "...", "APPLE_ID": "..." } }
     */
    @PostMapping("/run-chain")
    public Map<String, Object> runChain(@RequestBody ChainRequest request) {
        if (request.flows() == null || request.flows().isEmpty()) {
            throw new IllegalArgumentException("flows 不能为空，例如 [\"preflight\",\"erase\",\"activate\",\"poshmark\"]");
        }
        Map<String, String> variables = extractVariables(Map.of("variables", request.variables()));
        List<Map<String, Object>> reports = new ArrayList<>();
        for (String name : request.flows()) {
            WorkflowDefinition d = WorkflowPresets.get(name);
            if (d == null) d = customFlow.get(name);
            if (d == null) {
                throw new IllegalArgumentException("未知流程: " + name + "（既不是预设也不是自定义流程）");
            }
            WorkflowReport r = engine.execute(d, variables);
            reports.add(Map.of(
                    "flow", name,
                    "success", r.success(),
                    "steps", r.steps().size(),
                    "totalMs", r.totalMs()
            ));
            if (!r.success()) break;
        }
        boolean ok = reports.stream().allMatch(r -> (Boolean) r.get("success"));
        return Map.of("success", ok, "reports", reports);
    }

    /* ---------- 批量注册（读 Excel 逐行注册并更新文档） ---------- */

    private static final AtomicBoolean BATCH_RUNNING = new AtomicBoolean(false);
    private static final AtomicReference<Process> BATCH_PROC = new AtomicReference<>();
    private static final String BATCH_LOG = "batch-web-run.txt";

    /** 测试 Excel 写入：GET /api/workflow/test-excel-write?excelPath=xxx&row=26 */
    @GetMapping("/test-excel-write")
    public Map<String, Object> testExcelWrite(@RequestParam("excelPath") String excelPath,
                                                @RequestParam("row") int row) {
        try {
            java.io.File file = new java.io.File(excelPath);
            org.apache.poi.ss.usermodel.Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.FileInputStream(file));
            org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(0);
            org.apache.poi.ss.usermodel.Row rowObj = sheet.getRow(row);
            if (rowObj == null) rowObj = sheet.createRow(row);
            // 写状态列（F列，索引5）
            org.apache.poi.ss.usermodel.Cell statusCell = rowObj.getCell(5);
            if (statusCell == null) statusCell = rowObj.createCell(5);
            statusCell.setCellValue("测试成功");
            // 写备注列（G列，索引6）
            org.apache.poi.ss.usermodel.Cell noteCell = rowObj.getCell(6);
            if (noteCell == null) noteCell = rowObj.createCell(6);
            noteCell.setCellValue("这是测试写入的内容");
            // 保存
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                wb.write(fos);
            }
            wb.close();
            return Map.of("success", true, "message", "Excel 写入成功");
        } catch (Exception e) {
            return Map.of("success", false, "message", "写入失败: " + e.getMessage());
        }
    }

    /** 启动批量注册：body { excelPath, flow(默认22), continueOnError(默认false) }
     *  纯 Java 实现：读 Excel → 逐行跑流程 → 判成功 → 更新文档，输出写入 batch-web-run.txt。 */
    @PostMapping("/batch-register")
    public Map<String, Object> batchRegister(@RequestBody Map<String, Object> body) {
        String excelPath = body.get("excelPath") == null ? null : String.valueOf(body.get("excelPath"));
        if (excelPath == null || excelPath.isBlank()) {
            throw new IllegalArgumentException("excelPath 不能为空");
        }
        String flow = body.get("flow") == null ? "22" : String.valueOf(body.get("flow"));
        boolean cont = Boolean.TRUE.equals(body.get("continueOnError"));
        if (!BATCH_RUNNING.compareAndSet(false, true)) {
            throw new IllegalStateException("已有批量注册任务在执行中，请等待完成或刷新进度");
        }
        File workDir = new File(System.getProperty("user.dir"));
        File logFile = new File(workDir, BATCH_LOG);
        // 清空旧日志
        try { new FileOutputStream(logFile).close(); } catch (IOException ignored) {}
        // 后台线程跑批量注册
        Thread t = new Thread(() -> {
            try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(logFile, true), StandardCharsets.UTF_8))) {
                batchRegisterService.run(excelPath, flow, cont, line -> {
                    try {
                        bw.write(line);
                        bw.newLine();
                        bw.flush();
                    } catch (IOException ignored) {}
                });
            } catch (Exception e) {
                log.error("批量注册异常", e);
            } finally {
                BATCH_RUNNING.set(false);
            }
        });
        t.setDaemon(true);
        t.start();
        return Map.of("started", true, "flow", flow, "excelPath", excelPath, "log", BATCH_LOG);
    }

    /** 批量注册状态：{ running, output(日志全文) } */
    @GetMapping("/batch-register/status")
    public Map<String, Object> batchStatus() {
        File logFile = new File(System.getProperty("user.dir"), BATCH_LOG);
        StringBuilder sb = new StringBuilder();
        if (logFile.exists()) {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(logFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            } catch (Exception e) {
                sb.append("读取日志失败: ").append(e.getMessage());
            }
        }
        return Map.of("running", BATCH_RUNNING.get(), "output", sb.toString());
    }

    /* ---------- 单条调测（读 Excel 指定行 → 跑单条注册，前端可选行快速调测） ---------- */
    private static final AtomicBoolean SINGLE_RUNNING = new AtomicBoolean(false);
    private static final AtomicReference<Process> SINGLE_PROC = new AtomicReference<>();
    private static final String SINGLE_LOG = "single-web-run.txt";

    /** 读取 Excel 数据行列表: GET /api/workflow/excel-rows?excelPath=xxx */
    @GetMapping("/excel-rows")
    public Map<String, Object> excelRows(@RequestParam("excelPath") String excelPath) {
        if (excelPath == null || excelPath.isBlank()) {
            throw new IllegalArgumentException("excelPath 不能为空");
        }
        try {
            java.io.File file = new java.io.File(excelPath);
            org.apache.poi.ss.usermodel.Workbook wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(new java.io.FileInputStream(file));
            org.apache.poi.ss.usermodel.Sheet sheet = wb.getSheetAt(0);

            // 找表头行
            int headerRowIdx = -1;
            for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 20); i++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
                if (row == null) continue;
                for (org.apache.poi.ss.usermodel.Cell c : row) {
                    String v = getCellString(c);
                    if (v.contains("邮箱") || v.toLowerCase().contains("email")) {
                        headerRowIdx = i;
                        break;
                    }
                }
                if (headerRowIdx >= 0) break;
            }
            if (headerRowIdx < 0) {
                throw new IllegalArgumentException("找不到表头行（需含 邮箱/email 列）");
            }

            // 读数据行
            List<Map<String, Object>> rows = new ArrayList<>();
            for (int i = headerRowIdx + 1; i <= sheet.getLastRowNum(); i++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
                if (row == null) continue;
                String email = getCellString(row.getCell(0));
                if (email.isBlank()) continue;
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("email", email);
                r.put("password", getCellString(row.getCell(1)));
                r.put("firstName", getCellString(row.getCell(2)));
                r.put("lastName", getCellString(row.getCell(3)));
                r.put("username", getCellString(row.getCell(4)));
                r.put("status", getCellString(row.getCell(5)));
                rows.add(r);
            }
            wb.close();
            return Map.of("rows", rows, "total", rows.size());
        } catch (Exception e) {
            throw new RuntimeException("读取 Excel 失败: " + e.getMessage(), e);
        }
    }

    private String getCellString(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    /** 启动单条注册: POST /api/workflow/register-single { excelPath, row, flow, startIndex? } */
    @PostMapping("/register-single")
    public Map<String, Object> registerSingle(@RequestBody Map<String, Object> body) {
        String excelPath = body.get("excelPath") == null ? null : String.valueOf(body.get("excelPath"));
        Integer row = body.get("row") == null ? null : Integer.valueOf(String.valueOf(body.get("row")));
        String flow = body.get("flow") == null ? "google-register" : String.valueOf(body.get("flow"));
        Integer startIndex = body.get("startIndex") == null ? 0 : Integer.valueOf(String.valueOf(body.get("startIndex")));
        if (excelPath == null || excelPath.isBlank()) {
            throw new IllegalArgumentException("excelPath 不能为空");
        }
        if (row == null || row <= 0) {
            throw new IllegalArgumentException("row 必须为正整数");
        }
        if (!SINGLE_RUNNING.compareAndSet(false, true)) {
            throw new IllegalStateException("已有单条调测任务在执行中，请等待完成");
        }
        try {
            File workDir = new File(System.getProperty("user.dir"));
            File logFile = new File(workDir, SINGLE_LOG);
            List<String> cmd = new ArrayList<>();
            cmd.add(resolvePython());
            cmd.add("-u");
            cmd.add("single_register.py");
            cmd.add(excelPath);
            cmd.add("--row");
            cmd.add(String.valueOf(row));
            cmd.add("--flow");
            cmd.add(flow);
            if (startIndex != null && startIndex > 0) {
                cmd.add("--start-index");
                cmd.add(String.valueOf(startIndex));
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put("PYTHONUTF8", "1");
            pb.directory(workDir);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            SINGLE_PROC.set(p);
            Thread reader = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                     BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        bw.write(line);
                        bw.newLine();
                        bw.flush();
                    }
                } catch (Exception e) {
                    try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8))) {
                        bw.write("单条调测输出读取异常: " + e.getMessage());
                        bw.newLine();
                    } catch (Exception ignored) {
                    }
                } finally {
                    SINGLE_RUNNING.set(false);
                }
            });
            reader.setDaemon(true);
            reader.start();
            return Map.of("started", true, "row", row, "flow", flow, "excelPath", excelPath, "log", SINGLE_LOG);
        } catch (Exception e) {
            SINGLE_RUNNING.set(false);
            throw new RuntimeException("单条调测启动失败: " + e.getMessage(), e);
        }
    }

    /** 单条注册状态: GET /api/workflow/register-single/status */
    @GetMapping("/register-single/status")
    public Map<String, Object> singleStatus() {
        File logFile = new File(System.getProperty("user.dir"), SINGLE_LOG);
        StringBuilder sb = new StringBuilder();
        if (logFile.exists()) {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(logFile), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            } catch (Exception e) {
                sb.append("读取日志失败: ").append(e.getMessage());
            }
        }
        return Map.of("running", SINGLE_RUNNING.get(), "output", sb.toString());
    }

    /** 停止单条调测: POST /api/workflow/register-single/stop */
    @PostMapping("/register-single/stop")
    public Map<String, Object> registerSingleStop() {
        Process p = SINGLE_PROC.getAndSet(null);
        if (p != null && p.isAlive()) {
            p.destroy();
            try {
                p.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            if (p.isAlive()) p.destroyForcibly();
        }
        SINGLE_RUNNING.set(false);
        return Map.of("stopped", true);
    }

    /** 停止批量注册: POST /api/workflow/batch-register/stop */
    @PostMapping("/batch-register/stop")
    public Map<String, Object> batchStop() {
        // 通知 Java 服务停止
        batchRegisterService.stop();
        // 兼容旧的进程方式（如果有）
        Process p = BATCH_PROC.getAndSet(null);
        if (p != null && p.isAlive()) {
            p.destroy();
            try {
                p.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            if (p.isAlive()) p.destroyForcibly();
        }
        BATCH_RUNNING.set(false);
        return Map.of("stopped", true);
    }

    public record ChainRequest(List<String> flows, Map<String, String> variables) {}

    /** 解析可用的 python 命令：优先用户安装的常见路径，其次 Doubao 沙箱运行时自带的 python，最后回退 PATH 的 python。 */
    private static String resolvePython() {
        String local = System.getenv("LOCALAPPDATA");
        String[] fixed = new String[0];
        if (local != null) {
            fixed = new String[]{
                    local + "\\Programs\\Python\\Python311\\python.exe",
                    local + "\\Programs\\Python\\Python312\\python.exe",
                    local + "\\Programs\\Python\\Python313\\python.exe",
                    local + "\\Programs\\Python\\Python310\\python.exe"
            };
        }
        for (String p : fixed) {
            if (new File(p).exists()) return p;
        }
        File bases = local == null ? null : new File(local + "\\Doubao\\User Data\\sandbox_runtime\\bases");
        if (bases != null && bases.isDirectory()) {
            File[] dirs = bases.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File d : dirs) {
                    File cand = new File(d, "python\\python.exe");
                    if (cand.exists()) return cand.getAbsolutePath();
                }
            }
        }
        return "python";
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> extractVariables(Map<String, Object> body) {
        if (body == null) return Map.of();
        Object v = body.get("variables");
        if (!(v instanceof Map<?, ?> map)) return Map.of();
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) map).entrySet()) {
            result.put(String.valueOf(e.getKey()),
                    e.getValue() == null ? "" : String.valueOf(e.getValue()));
        }
        return result;
    }
}
