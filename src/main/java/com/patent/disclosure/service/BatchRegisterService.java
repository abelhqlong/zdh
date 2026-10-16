package com.patent.disclosure.service;

import com.patent.disclosure.automation.MouseController;
import com.patent.disclosure.workflow.CustomFlowService;
import com.patent.disclosure.workflow.WorkflowDefinition;
import com.patent.disclosure.workflow.WorkflowEngine;
import com.patent.disclosure.workflow.WorkflowReport;
import com.patent.disclosure.workflow.StepResult;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 纯 Java 批量注册服务（替代 batch_register.py）
 * 读 Excel → 逐行跑注册流程 → 判定成功 → 写回状态
 */
@Service
public class BatchRegisterService {

    private static final Logger log = LoggerFactory.getLogger(BatchRegisterService.class);

    // Excel 列索引（0基，和用户实际 Excel 一致）
    private static final int COL_EMAIL = 0;
    private static final int COL_PASSWORD = 1;
    private static final int COL_FIRST = 2;
    private static final int COL_LAST = 3;
    private static final int COL_USERNAME = 4;
    private static final int COL_STATUS = 5;
    private static final int COL_NOTE = 6;

    // Home 键坐标
    private static final int HOME_X = 1371;
    private static final int HOME_Y = 1023;

    private final CustomFlowService customFlow;
    private final WorkflowEngine engine;
    private final MouseController mouse;
    private final java.util.concurrent.atomic.AtomicBoolean stopFlag = new java.util.concurrent.atomic.AtomicBoolean(false);

    public BatchRegisterService(CustomFlowService customFlow, WorkflowEngine engine, MouseController mouse) {
        this.customFlow = customFlow;
        this.engine = engine;
        this.mouse = mouse;
    }

    /** 停止批量注册（由 Controller 调用） */
    public void stop() {
        stopFlag.set(true);
    }

    /**
     * 跑批量注册
     * @param excelPath Excel 文件绝对路径
     * @param flowName 流程名（默认 22）
     * @param continueOnError 失败后是否继续下一行
     * @param progressListener 进度日志回调
     */
    public void run(String excelPath, String flowName, boolean continueOnError, ProgressListener progressListener) {
        stopFlag.set(false); // 重置停止标志
        String flow = (flowName == null || flowName.isBlank()) ? "22" : flowName;
        File file = new File(excelPath);
        if (!file.exists()) {
            progressListener.onLog("[ERROR] File not found: " + excelPath);
            return;
        }

        // 备份（已禁用，不需要备份Excel）
        // try {
        //     String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        //     File backup = new File(file.getParentFile(),
        //             file.getName().replaceFirst("\\.[^.]+$", "") + "-backup-" + stamp + ".xlsx");
        //     Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        //     progressListener.onLog("[BACKUP] -> " + backup.getName());
        // } catch (IOException e) {
        //     progressListener.onLog("[WARN] Backup failed (ignored): " + e.getMessage());
        // }

        // 读 Excel
        List<RowData> rows;
        try {
            rows = readExcel(file);
        } catch (Exception e) {
            progressListener.onLog("[ERROR] Read Excel failed: " + e.getMessage());
            return;
        }
        progressListener.onLog("[INFO] Total rows: " + rows.size());

        // 找未注册行
        List<RowData> pending = new ArrayList<>();
        int alreadyDone = 0;
        int alreadyFailed = 0;
        for (RowData r : rows) {
            String status = r.status == null ? "" : r.status.trim();
            if (r.email.isBlank()) continue;
            // 判断是否已完成（成功或失败都算，下次跳过）
            boolean isSuccess = status.contains("成功") || status.equalsIgnoreCase("yes")
                    || status.equalsIgnoreCase("success") || status.equals("是");
            boolean isFail = status.contains("FAIL") || status.contains("失败") || status.contains("未找到");
            if (isSuccess) {
                alreadyDone++;
            } else if (isFail) {
                alreadyFailed++;
            } else {
                pending.add(r);
            }
        }
        if (pending.isEmpty()) {
            progressListener.onLog("[DONE] No pending accounts. " + alreadyDone + " success, " + alreadyFailed + " failed, all done.");
            return;
        }
        progressListener.onLog("[PENDING] " + pending.size() + " rows to register, " + alreadyDone + " success, " + alreadyFailed + " failed");

        int okCount = 0, failCount = 0;
        for (int i = 0; i < pending.size(); i++) {
            // 检查停止标志
            if (stopFlag.get()) {
                progressListener.onLog("\n[STOPPED] User stopped the batch.");
                break;
            }
            RowData row = pending.get(i);
            progressListener.onLog("\n===== [" + (i + 1) + "/" + pending.size() + "] " + row.email + " =====");

            // 回主屏
            resetHome();

            // 构造变量
            Map<String, String> vars = new HashMap<>();
            vars.put("EMAIL", row.email);
            vars.put("PASSWORD", row.password.isBlank() ? "Aa1231234." : row.password);
            vars.put("FIRST_NAME", row.firstName);
            vars.put("LAST_NAME", row.lastName);
            vars.put("USERNAME", row.username.isBlank() ? genUsername(row.firstName, row.lastName) : row.username);

            boolean ok;
            String failReason = "";
            try {
                progressListener.onLog("  [RUN] Starting flow...");
                // 重置 OCR 调用计数
                com.patent.disclosure.workflow.handler.BaseOcrHandler.resetOcrCallCount();
                WorkflowReport report = engine.execute(customFlow.get(flow), vars);
                ok = judgeSuccess(report);
                if (!ok) {
                    failReason = extractFailReason(report);
                }
            } catch (Exception e) {
                ok = false;
                failReason = e.getMessage();
                progressListener.onLog("  [ERROR] Exception: " + e.getMessage());
            }

            // 输出本次流程的 OCR 调用次数
            int ocrCount = com.patent.disclosure.workflow.handler.BaseOcrHandler.getOcrCallCount();

            if (ok) {
                progressListener.onLog("  [OK] Registration success | OCR 调用: " + ocrCount + " 次");
                updateRowResult(file, row.excelRowIndex, "成功", "");
                okCount++;
            } else {
                progressListener.onLog("  [FAIL] " + failReason + " | OCR 调用: " + ocrCount + " 次");
                updateRowResult(file, row.excelRowIndex, "失败", failReason);
                failCount++;
                if (!continueOnError) {
                    progressListener.onLog("\n[STOP] Stopped (set continueOnError=true to continue)");
                    break;
                }
            }

            // 点主屏幕回桌面
            try {
                java.awt.Robot robot = new java.awt.Robot();
                robot.mouseMove(1587, 1016);
                robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
                Thread.sleep(1000);
            } catch (Exception ignored) {}

            // 检查停止标志（行间间隔前）
            if (stopFlag.get()) {
                progressListener.onLog("\n[STOPPED] User stopped the batch.");
                break;
            }

            // 行间间隔
            try { Thread.sleep(4000); } catch (InterruptedException ignored) {}
        }

        progressListener.onLog("\n===== SUMMARY =====");
        progressListener.onLog("[OK] Success: " + okCount + " rows");
        progressListener.onLog("[FAIL] Failed: " + failCount + " rows");
        progressListener.onLog("[FILE] Updated: " + excelPath);
    }

    /** 判定注册成功：找到 CHECK "Add phone number" 且 success=true */
    private boolean judgeSuccess(WorkflowReport report) {
        if (report == null || report.steps() == null) return false;
        for (StepResult s : report.steps()) {
            if ("CHECK".equals(s.actionType())) {
                String tv = s.targetValue() == null ? "" : s.targetValue();
                String msg = s.message() == null ? "" : s.message();
                if (tv.contains("Add phone number") || msg.contains("Add phone number")) {
                    return s.success();
                }
            }
        }
        return false;
    }

    /** 提取失败原因（简化版，只保留最关键的问题） */
    private String extractFailReason(WorkflowReport report) {
        if (report == null || report.steps() == null) return "未知错误";
        for (StepResult s : report.steps()) {
            if (!s.success()) {
                String msg = s.message() == null ? s.actionType() : s.message();
                // 简化常见错误信息
                if (msg.contains("未找到可点击文字")) {
                    return "未找到按钮: " + msg.replace("未找到可点击文字: ", "");
                }
                if (msg.contains("IF_TEXT 条件未满足")) {
                    if (msg.contains("already tied")) return "邮箱已被注册";
                    if (msg.contains("提示")) return "弹窗处理失败";
                    return "页面检测失败";
                }
                if (msg.contains("检查未通过")) {
                    return "注册结果确认失败";
                }
                if (msg.length() > 50) {
                    return msg.substring(0, 50) + "...";
                }
                return msg;
            }
        }
        return "未知错误";
    }

    /** 回主屏 */
    private void resetHome() {
        try {
            mouse.click(HOME_X, HOME_Y);
            Thread.sleep(3000);
        } catch (Exception e) {
            log.warn("回主屏失败(忽略): {}", e.getMessage());
        }
    }

    /** 读 Excel，返回数据行列表 */
    private List<RowData> readExcel(File file) throws IOException {
        List<RowData> result = new ArrayList<>();
        try (Workbook wb = WorkbookFactory.create(new FileInputStream(file))) {
            Sheet sheet = wb.getSheetAt(0);
            // 找表头行（含"邮箱"字样）
            int headerRowIdx = -1;
            for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 20); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                for (Cell c : row) {
                    String v = getCellString(c);
                    if (v.contains("邮箱") || v.toLowerCase().contains("email")) {
                        headerRowIdx = i;
                        break;
                    }
                }
                if (headerRowIdx >= 0) break;
            }
            if (headerRowIdx < 0) {
                throw new IOException("找不到表头行（需含 邮箱/email 列）");
            }
            // 读数据行
            for (int i = headerRowIdx + 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                String email = getCellString(row.getCell(COL_EMAIL));
                if (email.isBlank()) continue;
                RowData rd = new RowData();
                rd.excelRowIndex = i;
                rd.email = email;
                rd.password = getCellString(row.getCell(COL_PASSWORD));
                rd.firstName = getCellString(row.getCell(COL_FIRST));
                rd.lastName = getCellString(row.getCell(COL_LAST));
                rd.username = getCellString(row.getCell(COL_USERNAME));
                rd.status = getCellString(row.getCell(COL_STATUS));
                result.add(rd);
            }
        }
        return result;
    }

    /** 更新某一行的状态列 */
    private void updateRowStatus(File file, int rowIndex, String status) {
        try (Workbook wb = WorkbookFactory.create(new FileInputStream(file))) {
            Sheet sheet = wb.getSheetAt(0);
            Row row = sheet.getRow(rowIndex);
            if (row == null) row = sheet.createRow(rowIndex);
            Cell cell = row.getCell(COL_STATUS);
            if (cell == null) cell = row.createCell(COL_STATUS);
            cell.setCellValue(status);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                wb.write(fos);
            }
        } catch (IOException e) {
            log.error("更新 Excel 失败: {}", e.getMessage());
        }
    }

    /** 同时写状态列和备注列 */
    private void updateRowResult(File file, int rowIndex, String status, String note) {
        System.out.println("[DEBUG] updateRowResult: file=" + file.getAbsolutePath() + ", rowIndex=" + rowIndex + ", status=" + status);
        try (Workbook wb = WorkbookFactory.create(new FileInputStream(file))) {
            Sheet sheet = wb.getSheetAt(0);
            Row row = sheet.getRow(rowIndex);
            if (row == null) row = sheet.createRow(rowIndex);
            // 状态列
            Cell statusCell = row.getCell(COL_STATUS);
            if (statusCell == null) statusCell = row.createCell(COL_STATUS);
            statusCell.setCellValue(status);
            // 备注列
            Cell noteCell = row.getCell(COL_NOTE);
            if (noteCell == null) noteCell = row.createCell(COL_NOTE);
            noteCell.setCellValue(note);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                wb.write(fos);
            }
            System.out.println("[DEBUG] updateRowResult: 写入成功");
        } catch (IOException e) {
            log.error("更新 Excel 失败: {}", e.getMessage());
            System.err.println("[ERROR] 更新 Excel 失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private String getCellString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private String genUsername(String first, String last) {
        String f = first == null ? "" : first.toLowerCase().replaceAll("[^a-z0-9]", "").substring(0, Math.min(6, first.length()));
        String l = last == null ? "" : last.toLowerCase().replaceAll("[^a-z0-9]", "");
        int remain = 10 - f.length();
        if (remain <= 0) return f.substring(0, 10);
        return (f + l.substring(0, Math.min(remain, l.length())));
    }

    @FunctionalInterface
    public interface ProgressListener {
        void onLog(String line);
    }

    private static class RowData {
        int excelRowIndex;
        String email = "";
        String password = "";
        String firstName = "";
        String lastName = "";
        String username = "";
        String status = "";
    }
}
