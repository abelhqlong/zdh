package com.patent.disclosure.workflow;

import java.util.Map;

/** 步骤 config 读取工具（整数/长整型/字符串），供各 handler 与引擎共用。 */
public final class StepConfigs {
    private StepConfigs() {}

    public static int configInt(WorkflowStep step, String key, int def) {
        Object v = config(step, key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    public static long configLong(WorkflowStep step, String key, long def) {
        Object v = config(step, key);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return def;
    }

    public static String configStr(WorkflowStep step, String key, String def) {
        Object v = config(step, key);
        if (v == null) return def;
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }

    private static Object config(WorkflowStep step, String key) {
        Map<String, Object> cfg = step.config();
        return cfg == null ? null : cfg.get(key);
    }
}
