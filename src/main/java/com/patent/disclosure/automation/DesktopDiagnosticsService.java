package com.patent.disclosure.automation;

import org.springframework.stereotype.Service;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DesktopDiagnosticsService {
    private final MouseController mouseController;
    private final KeyboardController keyboardController;
    private final ScreenCapture screenCapture;

    public DesktopDiagnosticsService(MouseController mouseController,
                                     KeyboardController keyboardController,
                                     ScreenCapture screenCapture) {
        this.mouseController = mouseController;
        this.keyboardController = keyboardController;
        this.screenCapture = screenCapture;
    }

    public Map<String, Object> diagnostics() {
        Map<String, Object> result = new LinkedHashMap<>();

        boolean headless = GraphicsEnvironment.isHeadless();
        result.put("platform", PlatformInfo.platform());
        result.put("osName", PlatformInfo.osName());
        result.put("javaVersion", System.getProperty("java.version"));
        result.put("java.awt.headless", System.getProperty("java.awt.headless"));
        result.put("graphicsEnvironmentHeadless", headless);

        int screenCount = 0;
        String screenError = null;
        if (!headless) {
            try {
                GraphicsDevice[] devices =
                        GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
                screenCount = devices.length;
            } catch (Throwable t) {
                screenError = describe(t);
            }
        }
        result.put("screenCount", screenCount);
        if (screenError != null) result.put("screenError", screenError);

        boolean desktopSessionAvailable = !headless && screenCount > 0;
        result.put("desktopSessionAvailable", desktopSessionAvailable);

        boolean mouse = mouseController.isAvailable();
        boolean keyboard = keyboardController.isAvailable();
        boolean capture = screenCapture.isAvailable();

        result.put("mouseRobotAvailable", mouse);
        result.put("keyboardRobotAvailable", keyboard);
        result.put("screenCaptureAvailable", capture);

        if (!desktopSessionAvailable) {
            result.put("message", headless
                    ? "Java 当前处于 headless 模式，暂时不能执行桌面自动化"
                    : "Java 不是 headless，但当前没有可用的显示设备");
        } else if (!mouse || !keyboard || !capture) {
            result.put("message", "桌面会话已检测到，但 Robot 或屏幕捕获仍不可用；macOS 可能需要辅助功能/屏幕录制权限");
            result.put("mouseRobotError", describe(mouseController.probeFailure()));
            result.put("keyboardRobotError", describe(keyboardController.probeFailure()));
            result.put("screenCaptureError", describe(screenCapture.probeFailure()));
        } else {
            result.put("message", "桌面自动化环境可用");
        }

        return result;
    }

    private String describe(Throwable t) {
        if (t == null) return null;
        return t.getClass().getName() + ": " + t.getMessage();
    }
}
