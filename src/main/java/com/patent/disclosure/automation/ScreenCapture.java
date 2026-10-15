package com.patent.disclosure.automation;

import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class ScreenCapture {
    private final RobotHolder robotHolder = new RobotHolder();

    public BufferedImage capture() throws AWTException {
        Rectangle bounds = virtualScreenBounds();
        return robotHolder.getOrCreate().createScreenCapture(bounds);
    }

    public BufferedImage capture(Rectangle rectangle) throws AWTException {
        return robotHolder.getOrCreate().createScreenCapture(rectangle);
    }

    public Rectangle virtualScreenBounds() {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] devices = ge.getScreenDevices();
        if (devices.length == 0) {
            throw new IllegalStateException("No display device is available.");
        }

        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        for (GraphicsDevice device : devices) {
            Rectangle b = device.getDefaultConfiguration().getBounds();
            minX = Math.min(minX, b.x);
            minY = Math.min(minY, b.y);
            maxX = Math.max(maxX, b.x + b.width);
            maxY = Math.max(maxY, b.y + b.height);
        }
        return new Rectangle(minX, minY, maxX - minX, maxY - minY);
    }

    public byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    public boolean isAvailable() {
        try {
            if (GraphicsEnvironment.isHeadless()) return false;
            if (GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices().length == 0) return false;
            robotHolder.getOrCreate();
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public Throwable probeFailure() {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return new IllegalStateException("GraphicsEnvironment reports headless=true");
            }
            if (GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices().length == 0) {
                return new IllegalStateException("No screen device detected");
            }
            robotHolder.getOrCreate();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
