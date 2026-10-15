package com.patent.disclosure.workflow;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流程执行上下文：变量表（含内置随机变量）、循环计数器、标签索引、防死循环计数。
 *
 * L3 变量层：
 *   - 内置变量（每次流程执行生成一次，流程内值稳定）：
 *       ${RANDOM_EMAIL}      随机邮箱（如 wj28f3qa@icloud.com）
 *       ${RANDOM_PASSWORD}   随机密码（12 位字母+数字，兼容键盘输入）
 *       ${RANDOM_USER}       随机用户名（8 位小写字母数字）
 *       ${RANDOM_NUMBER:n}   随机 n 位数字（同 key 流程内稳定）
 *       ${TIMESTAMP}         毫秒时间戳
 *       ${NOW}               当前时间 yyyyMMddHHmmss
 *   - 用户变量：构造时注入（IP/APPLE_ID/PASSWORD 等），EXTRACT_TEXT / SET_VAR 可写入
 *   - 支持嵌套：变量值里再出现 ${VAR} 会继续解析（最多 3 轮）
 */
public class WorkflowContext {

    /** 防死循环：单次流程最多执行的步骤次数 */
    public static final int MAX_STEPS = 5000;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String[] EMAIL_DOMAINS =
            {"icloud.com", "outlook.com", "gmail.com", "qq.com", "163.com", "hotmail.com"};
    private static final Pattern RANDOM_NUMBER_PATTERN = Pattern.compile("\\$\\{RANDOM_NUMBER:(\\d{1,2})\\}");

    private final Map<String, String> variables = new LinkedHashMap<>();
    private final Map<String, Integer> repeatCounts = new HashMap<>();
    /** 标签名 → 步骤数组下标 */
    private final Map<String, Integer> labelIndex = new LinkedHashMap<>();
    private int stepsExecuted = 0;

    public WorkflowContext(Map<String, String> variables) {
        if (variables != null) this.variables.putAll(variables);
        this.variables.put("RANDOM_EMAIL", randomEmail());
        this.variables.put("RANDOM_PASSWORD", randomPassword());
        this.variables.put("RANDOM_USER", randomString(8, "abcdefghijklmnopqrstuvwxyz0123456789"));
        this.variables.put("TIMESTAMP", String.valueOf(System.currentTimeMillis()));
        this.variables.put("NOW", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")));
    }

    /** 写入/覆盖变量（EXTRACT_TEXT、SET_VAR 使用） */
    public void setVar(String name, String value) {
        if (name == null || name.isBlank()) return;
        variables.put(name.trim(), value == null ? "" : value);
    }

    public String getVar(String name) {
        return variables.get(name);
    }

    /** 变量替换：${KEY} → 值；未定义替换为空串；支持嵌套与 ${RANDOM_NUMBER:n} 动态生成 */
    public String resolve(String value) {
        if (value == null) return null;
        String out = value;
        for (int round = 0; round < 3; round++) {
            if (!out.contains("${")) break;
            // 动态随机数字：${RANDOM_NUMBER:n} 生成一次并缓存
            Matcher m = RANDOM_NUMBER_PATTERN.matcher(out);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String key = m.group(0);
                if (!variables.containsKey(key)) {
                    int n = Math.max(1, Math.min(20, Integer.parseInt(m.group(1))));
                    variables.put(key, randomNumber(n));
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(variables.get(key)));
            }
            m.appendTail(sb);
            out = sb.toString();
            for (Map.Entry<String, String> e : variables.entrySet()) {
                out = out.replace("${" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
        }
        return out;
    }

    /** 记录标签位置（重复标签后者覆盖，并在 map 中保持首次顺序） */
    public void indexLabel(String label, int index) {
        if (label == null || label.isBlank()) return;
        labelIndex.remove(label.trim());
        labelIndex.put(label.trim(), index);
    }

    public boolean hasLabel(String label) {
        return label != null && labelIndex.containsKey(label.trim());
    }

    public Integer labelIndex(String label) {
        return label == null ? null : labelIndex.get(label.trim());
    }

    /** 循环计数器 */
    public int repeatCount(String key) {
        return repeatCounts.getOrDefault(key, 0);
    }

    public void incRepeat(String key) {
        repeatCounts.merge(key, 1, Integer::sum);
    }

    public void resetRepeat(String key) {
        repeatCounts.remove(key);
    }

    /** 返回已执行步骤总数（用于防死循环），并自增 */
    public int bumpExecuted() {
        return ++stepsExecuted;
    }

    // ---------- 随机生成 ----------

    private static String randomEmail() {
        return randomString(8, "abcdefghijklmnopqrstuvwxyz0123456789")
                + "@" + EMAIL_DOMAINS[RANDOM.nextInt(EMAIL_DOMAINS.length)];
    }

    private static String randomPassword() {
        // 12 位：大小写字母 + 数字（仅用 KeyboardController.type 支持的字符）
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String digits = "0123456789";
        StringBuilder sb = new StringBuilder();
        sb.append(lower.charAt(RANDOM.nextInt(lower.length())));
        sb.append(upper.charAt(RANDOM.nextInt(upper.length())));
        sb.append(digits.charAt(RANDOM.nextInt(digits.length())));
        String pool = lower + upper + digits;
        for (int i = 3; i < 12; i++) sb.append(pool.charAt(RANDOM.nextInt(pool.length())));
        return sb.toString();
    }

    private static String randomNumber(int digits) {
        StringBuilder sb = new StringBuilder();
        sb.append(digits == 1 ? RANDOM.nextInt(10) : 1 + RANDOM.nextInt(9));
        for (int i = 1; i < digits; i++) sb.append(RANDOM.nextInt(10));
        return sb.toString();
    }

    private static String randomString(int len, String pool) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append(pool.charAt(RANDOM.nextInt(pool.length())));
        return sb.toString();
    }
}
