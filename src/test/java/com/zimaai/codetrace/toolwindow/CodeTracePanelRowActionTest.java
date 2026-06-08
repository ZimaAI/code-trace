package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.awt.Component;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeTracePanelRowActionTest {
    @TempDir
    Path tempDir;

    @Test
    void tableInstallsActionColumnRendererAndEditor() {
        CodeTracePanel panel = panelFor(documentWithTwoNodes());
        JTable table = panel.editorPanel().nodeTable();

        assertEquals("操作", table.getColumnModel().getColumn(3).getHeaderValue());
        assertEquals(NodeRowActionsRenderer.class, table.getColumnModel().getColumn(3).getCellRenderer().getClass());
        assertEquals(NodeRowActionsEditor.class, table.getColumnModel().getColumn(3).getCellEditor().getClass());
    }

    @Test
    void moveDownRowActionMovesClickedRowNodeEvenWhenAnotherRowIsSelected() {
        CodeTracePanel panel = panelFor(documentWithTwoNodes());
        panel.editorPanel().nodeTable().setRowSelectionInterval(1, 1);

        clickRowAction(panel, 0, NodeRowAction.MOVE_DOWN);

        TraceDocument document = controller(panel).state().currentDocument();
        assertEquals("node-2", document.nodes().get(0).id());
        assertEquals("node-1", document.nodes().get(1).id());
    }

    @Test
    void deleteRowActionRequiresConfirmationForClickedRowNode() {
        CodeTracePanel panel = panelFor(documentWithTwoNodes());
        AtomicReference<TraceNode> confirmationNode = new AtomicReference<>();
        panel.editorPanel().nodeTable().setRowSelectionInterval(1, 1);
        panel.setConfirmNodeDeleteForTest(node -> {
            confirmationNode.set(node);
            return false;
        });

        clickRowAction(panel, 0, NodeRowAction.DELETE);

        assertEquals("node-1", confirmationNode.get().id());
        assertEquals(2, controller(panel).state().currentDocument().nodes().size());

        panel.setConfirmNodeDeleteForTest(node -> true);
        clickRowAction(panel, 0, NodeRowAction.DELETE);

        TraceDocument document = controller(panel).state().currentDocument();
        assertEquals(1, document.nodes().size());
        assertEquals("node-2", document.nodes().get(0).id());
        assertFalse(panel.editorPanel().goToLinkedButton().isEnabled());
    }

    private CodeTracePanel panelFor(TraceDocument document) {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", document);
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        CodeTracePanel panel = new CodeTracePanel(controller);
        panel.reloadFromDisk();
        return panel;
    }

    private static void clickRowAction(CodeTracePanel panel, int row, NodeRowAction action) {
        JTable table = panel.editorPanel().nodeTable();
        Object value = table.getValueAt(row, 3);
        Component component = table.getColumnModel().getColumn(3).getCellEditor()
                .getTableCellEditorComponent(table, value, true, row, 3);
        ((NodeRowActionsPanel) component).button(action).doClick();
    }

    private static CodeTraceController controller(CodeTracePanel panel) {
        try {
            java.lang.reflect.Field field = CodeTracePanel.class.getDeclaredField("controller");
            field.setAccessible(true);
            return (CodeTraceController) field.get(panel);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static TraceDocument documentWithTwoNodes() {
        return new TraceDocument(
                3,
                "trace-1",
                "Trace 1",
                "",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(
                        new TraceNode("node-1", "first", "A#a", "a()", "A.java", 10, "JAVA", "", "A#a"),
                        new TraceNode("node-2", "second", "B#b", "b()", "B.java", 20, "JAVA", "", "B#b")),
                List.of(),
                Set.of());
    }
}
