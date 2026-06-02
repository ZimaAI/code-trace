package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeTracePanelTest {
    @TempDir
    Path tempDir;

    @Test
    void keepsRefreshAndFileListToggleInTopToolbar() {
        assertEquals(List.of("Refresh", "Toggle Files"), CodeTracePanel.topToolbarButtonLabels());
        assertNull(findTopToolbarLabel("Save Trace Note"));
        assertNull(findTopToolbarLabel("Save Node Note"));
        assertNull(findTopToolbarLabel("Set as Source"));
        assertNull(findTopToolbarLabel("Link To Here"));
        assertNull(findTopToolbarLabel("Unlink"));
    }

    @Test
    void keepsSaveButtonsInsideEditorPanel() {
        TraceEditorPanel editorPanel = new TraceEditorPanel();

        assertNotNull(editorPanel.saveTraceNoteButton());
        assertNotNull(editorPanel.saveNodeNoteButton());
    }

    @Test
    void placesFileListToggleButtonInTopToolbar() {
        CodeTracePanel panel = panelFor(documentWithOneNode());

        JButton toggleButton = findNamedButton(panel.getComponent(), "file-list-toggle-button");
        JPanel toolbar = (JPanel) ((Container) panel.getComponent()).getComponent(0);

        assertNotNull(toggleButton);
        assertEquals(toolbar, toggleButton.getParent());
    }

    private static String findTopToolbarLabel(String label) {
        return CodeTracePanel.topToolbarButtonLabels().stream()
                .filter(label::equals)
                .findFirst()
                .orElse(null);
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

    @Test
    void preservesPreferredSelectedNodeIdAcrossExternalRefresh() throws Exception {
        CodeTracePanel panel = panelFor(documentWithOneNode());
        CodeTraceController controller = controller(panel);

        controller.preferSelectedNode("node-1");
        panel.refreshFromExternalAction();

        assertNull(controller.state().preferredSelectedNodeId());
        assertEquals("node-1", controller.focusedNodeId());
    }

    private static CodeTraceController controller(CodeTracePanel panel) throws Exception {
        Field field = CodeTracePanel.class.getDeclaredField("controller");
        field.setAccessible(true);
        return (CodeTraceController) field.get(panel);
    }

    private static TraceEditorPanel editorPanel(CodeTracePanel panel) throws Exception {
        Field field = CodeTracePanel.class.getDeclaredField("editorPanel");
        field.setAccessible(true);
        return (TraceEditorPanel) field.get(panel);
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
}
