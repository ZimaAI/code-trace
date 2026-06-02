package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.intellij.ui.JBSplitter;
import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import javax.swing.JButton;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeTracePanelFileListToggleTest {
    @TempDir
    Path tempDir;

    @Test
    void collapsesFileListToZeroAndRestoresPreviousWidth() {
        CodeTracePanel panel = panelFor(documentWithOneNode());
        panel.getComponent().setSize(1200, 800);
        layoutRecursively(panel.getComponent());

        JBSplitter splitter = findComponent(panel.getComponent(), JBSplitter.class);
        JButton toggleButton = findNamedButton(panel.getComponent(), "file-list-toggle-button");

        assertNotNull(splitter);
        assertNotNull(toggleButton);
        assertTrue(splitter.getFirstComponent().getWidth() > 0);

        float expandedProportion = splitter.getProportion();
        toggleButton.doClick();
        layoutRecursively(panel.getComponent());

        assertEquals(0.0f, splitter.getProportion(), 0.0001f);
        assertEquals(0, splitter.getFirstComponent().getWidth());
        assertEquals("Expand file list", toggleButton.getToolTipText());

        toggleButton.doClick();
        layoutRecursively(panel.getComponent());

        assertEquals(expandedProportion, splitter.getProportion(), 0.0001f);
        assertTrue(splitter.getFirstComponent().getWidth() > 0);
        assertEquals("Collapse file list", toggleButton.getToolTipText());
    }

    private CodeTracePanel panelFor(TraceDocument document) {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", document);
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        CodeTracePanel panel = new CodeTracePanel(controller);
        panel.reloadFromDisk();
        return panel;
    }

    private static TraceDocument documentWithOneNode() {
        return new TraceDocument(
                3,
                "trace-1",
                "Trace 1",
                "",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(new TraceNode("node-1", "single line", "A#a", "a()", "A.java", 10, "JAVA", "", "A#a")),
                List.of(),
                java.util.Set.of());
    }

    private static JButton findNamedButton(Component component, String name) {
        if (component instanceof JButton button && name.equals(button.getName())) {
            return button;
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JButton match = findNamedButton(child, name);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static <T extends Component> T findComponent(Component component, Class<T> type) {
        if (type.isInstance(component)) {
            return type.cast(component);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                T match = findComponent(child, type);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static void layoutRecursively(Component component) {
        component.doLayout();
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                layoutRecursively(child);
            }
        }
    }
}
