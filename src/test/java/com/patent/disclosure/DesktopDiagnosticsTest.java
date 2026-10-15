package com.patent.disclosure;

import org.junit.jupiter.api.Test;
import java.awt.GraphicsEnvironment;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DesktopDiagnosticsTest {
    @Test
    void graphicsEnvironmentIsReadable() {
        assertNotNull(GraphicsEnvironment.getLocalGraphicsEnvironment());
    }
}
