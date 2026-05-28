package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class PluginDescriptorTest {
    @Test
    void pluginXmlRegistersCodeTraceToolWindow() throws IOException {
        try (var stream = getClass().getResourceAsStream("/META-INF/plugin.xml")) {
            assertTrue(stream != null, "plugin.xml should exist");
            String xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(xml.contains("<id>com.zimaai.codetrace</id>"));
            assertTrue(xml.contains("<toolWindow"));
            assertTrue(xml.contains("id=\"code-trace\""));
            assertTrue(xml.contains("factoryClass=\"com.zimaai.codetrace.toolwindow.CodeTraceToolWindowFactory\""));
            assertTrue(xml.contains("serviceImplementation=\"com.zimaai.codetrace.toolwindow.CodeTraceProjectService\""));
            assertTrue(xml.contains("class=\"com.zimaai.codetrace.actions.AddToCodeTraceAction\""));
            assertTrue(xml.contains("group-id=\"EditorPopupMenu\""));
        }
    }
}
