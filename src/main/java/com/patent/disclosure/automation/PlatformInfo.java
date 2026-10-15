package com.patent.disclosure.automation;

public final class PlatformInfo {
    private PlatformInfo() {}

    public static String osName() {
        return System.getProperty("os.name", "unknown");
    }

    public static String platform() {
        String os = osName().toLowerCase();
        if (os.contains("mac")) return "macOS";
        if (os.contains("win")) return "Windows";
        if (os.contains("linux")) return "Linux";
        return osName();
    }
}
