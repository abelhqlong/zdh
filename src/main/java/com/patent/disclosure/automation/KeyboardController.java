package com.patent.disclosure.automation;

import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.event.KeyEvent;

@Component
public class KeyboardController {
    private final RobotHolder robotHolder = new RobotHolder();

    public void type(String text) throws AWTException {
        var robot = robotHolder.getOrCreate();
        for (char c : text.toCharArray()) {
            typeChar(robot, c);
            // 字符间隔：防止模拟器端丢键/乱序（快发会导致 ngj... 漏字符）
            try {
                Thread.sleep(40);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void pasteText(String text) throws AWTException {
        // Clipboard integration can be added later. For now type supported characters.
        type(text);
    }

    /** 按名称发送按键：ENTER/TAB/ESC/BACKSPACE/SPACE/HOME/END/PAGE_UP/PAGE_DOWN/UP/DOWN/LEFT/RIGHT/F1-F12 等。 */
    public void pressKey(String name) throws AWTException {
        var robot = robotHolder.getOrCreate();
        int code = keyCode(name);
        if (code == java.awt.event.KeyEvent.VK_UNDEFINED) {
            throw new IllegalArgumentException("不支持的按键名: " + name);
        }
        press(robot, code);
    }

    private int keyCode(String name) {
        if (name == null) return java.awt.event.KeyEvent.VK_UNDEFINED;
        String n = name.trim().toUpperCase().replace("-", "").replace("_", "");
        return switch (n) {
            case "ENTER", "RETURN" -> java.awt.event.KeyEvent.VK_ENTER;
            case "TAB" -> java.awt.event.KeyEvent.VK_TAB;
            case "ESC", "ESCAPE" -> java.awt.event.KeyEvent.VK_ESCAPE;
            case "BACKSPACE", "BS" -> java.awt.event.KeyEvent.VK_BACK_SPACE;
            case "DELETE", "DEL" -> java.awt.event.KeyEvent.VK_DELETE;
            case "SPACE" -> java.awt.event.KeyEvent.VK_SPACE;
            case "HOME" -> java.awt.event.KeyEvent.VK_HOME;
            case "END" -> java.awt.event.KeyEvent.VK_END;
            case "PAGEUP", "PGUP" -> java.awt.event.KeyEvent.VK_PAGE_UP;
            case "PAGEDOWN", "PGDN" -> java.awt.event.KeyEvent.VK_PAGE_DOWN;
            case "UP" -> java.awt.event.KeyEvent.VK_UP;
            case "DOWN" -> java.awt.event.KeyEvent.VK_DOWN;
            case "LEFT" -> java.awt.event.KeyEvent.VK_LEFT;
            case "RIGHT" -> java.awt.event.KeyEvent.VK_RIGHT;
            case "F1" -> java.awt.event.KeyEvent.VK_F1;
            case "F2" -> java.awt.event.KeyEvent.VK_F2;
            case "F3" -> java.awt.event.KeyEvent.VK_F3;
            case "F4" -> java.awt.event.KeyEvent.VK_F4;
            case "F5" -> java.awt.event.KeyEvent.VK_F5;
            case "F6" -> java.awt.event.KeyEvent.VK_F6;
            case "F7" -> java.awt.event.KeyEvent.VK_F7;
            case "F8" -> java.awt.event.KeyEvent.VK_F8;
            case "F9" -> java.awt.event.KeyEvent.VK_F9;
            case "F10" -> java.awt.event.KeyEvent.VK_F10;
            case "F11" -> java.awt.event.KeyEvent.VK_F11;
            case "F12" -> java.awt.event.KeyEvent.VK_F12;
            default -> java.awt.event.KeyEvent.VK_UNDEFINED;
        };
    }

    private void typeChar(java.awt.Robot robot, char c) {
        if (Character.isLetterOrDigit(c)) {
            boolean upper = Character.isUpperCase(c);
            int keyCode = KeyEvent.getExtendedKeyCodeForChar(Character.toUpperCase(c));
            if (keyCode == KeyEvent.VK_UNDEFINED) {
                throw new IllegalArgumentException("Unsupported character: " + c);
            }
            if (upper) robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyPress(keyCode);
            robot.keyRelease(keyCode);
            if (upper) robot.keyRelease(KeyEvent.VK_SHIFT);
            return;
        }
        switch (c) {
            case ' ' -> press(robot, KeyEvent.VK_SPACE);
            case '-' -> press(robot, KeyEvent.VK_MINUS);
            case '_' -> { robot.keyPress(KeyEvent.VK_SHIFT); press(robot, KeyEvent.VK_MINUS); robot.keyRelease(KeyEvent.VK_SHIFT); }
            case '.' -> press(robot, KeyEvent.VK_PERIOD);
            case '@' -> { robot.keyPress(KeyEvent.VK_SHIFT); press(robot, KeyEvent.VK_2); robot.keyRelease(KeyEvent.VK_SHIFT); }
            case ':' -> { robot.keyPress(KeyEvent.VK_SHIFT); press(robot, KeyEvent.VK_SEMICOLON); robot.keyRelease(KeyEvent.VK_SHIFT); }
            case '/' -> press(robot, KeyEvent.VK_SLASH);
            case '\\' -> press(robot, KeyEvent.VK_BACK_SLASH);
            default -> throw new IllegalArgumentException("Unsupported character: " + c);
        }
    }

    private void press(java.awt.Robot robot, int code) {
        robot.keyPress(code);
        robot.keyRelease(code);
    }

    public boolean isAvailable() {
        return robotHolder.isAvailable();
    }

    public Throwable probeFailure() {
        return robotHolder.probeFailure();
    }
}
