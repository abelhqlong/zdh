package com.patent.disclosure.automation;

import java.awt.AWTException;
import java.awt.Robot;

public class RobotHolder {
    private volatile Robot robot;

    public Robot getOrCreate() throws AWTException {
        Robot current = robot;
        if (current != null) return current;
        synchronized (this) {
            if (robot == null) {
                robot = new Robot();
                robot.setAutoDelay(50);
            }
            return robot;
        }
    }

    public boolean isAvailable() {
        try {
            getOrCreate();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public Throwable probeFailure() {
        try {
            getOrCreate();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
