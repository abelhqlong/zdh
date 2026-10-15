package com.patent.disclosure.automation;

import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.event.InputEvent;

@Component
public class MouseController {
    private final RobotHolder robotHolder = new RobotHolder();

    public void move(int x, int y) throws AWTException {
        robotHolder.getOrCreate().mouseMove(x, y);
    }

    public void click(int x, int y) throws AWTException {
        var robot = robotHolder.getOrCreate();
        robot.mouseMove(x, y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    public void doubleClick(int x, int y) throws AWTException {
        var robot = robotHolder.getOrCreate();
        robot.mouseMove(x, y);
        for (int i = 0; i < 2; i++) {
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(60);
        }
    }

    public void rightClick(int x, int y) throws AWTException {
        var robot = robotHolder.getOrCreate();
        robot.mouseMove(x, y);
        robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
    }

    public void drag(int x1, int y1, int x2, int y2, long durationMs) throws AWTException {
        var robot = robotHolder.getOrCreate();
        robot.mouseMove(x1, y1);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        int steps = Math.max(2, (int) Math.max(1, durationMs / 10));
        long stepMs = Math.max(1, durationMs / steps);
        for (int i = 1; i <= steps; i++) {
            robot.mouseMove(x1 + (x2 - x1) * i / steps, y1 + (y2 - y1) * i / steps);
            robot.delay((int) stepMs);
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    public boolean isAvailable() {
        return robotHolder.isAvailable();
    }

    public Throwable probeFailure() {
        return robotHolder.probeFailure();
    }
}
