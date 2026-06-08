package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.border.CompoundBorder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeTracePanelLinkSourceStatusTest {
    @TempDir
    Path tempDir;

    @Test
    void showsGuidanceAndDisablesLinkToHereWhenNoSourceIsSet() {
        CodeTracePanel panel = panelFor(documentWithTwoNodes());
        panel.editorPanel().nodeTable().setRowSelectionInterval(0, 0);

        assertFalse(panel.editorPanel().linkToHereButton().isEnabled());
        assertTrue(panel.editorPanel().linkStatus().getText().contains("链接源：未设置"));
        assertTrue(panel.editorPanel().linkStatus().getText().contains("Set as Source"));
    }

    @Test
    void settingSourceShowsNumberAndNodeName() {
        CodeTracePanel panel = panelFor(documentWithTwoNodes());
        panel.editorPanel().nodeTable().setRowSelectionInterval(0, 0);

        panel.editorPanel().setAsSourceButton().doClick();

        assertTrue(panel.editorPanel().linkToHereButton().isEnabled());
        assertTrue(panel.editorPanel().linkStatus().getText().contains("链接源：#1 first"));
    }

    @Test
    void pendingSourceRendererShowsLeftStripe() {
        CodeTracePanel panel = panelFor(documentWithTwoNodes());
        JTable table = panel.editorPanel().nodeTable();
        table.setRowSelectionInterval(0, 0);
        panel.editorPanel().setAsSourceButton().doClick();

        Component component = table.getColumnModel().getColumn(1).getCellRenderer()
                .getTableCellRendererComponent(table, table.getValueAt(0, 1), false, false, 0, 1);

        JLabel label = assertInstanceOf(JLabel.class, component);
        assertInstanceOf(CompoundBorder.class, label.getBorder());
    }

    @Test
    void deletingSourceClearsLinkSourceState() {
        CodeTracePanel panel = panelFor(documentWithTwoNodes());
        panel.setConfirmNodeDeleteForTest(node -> true);
        panel.editorPanel().nodeTable().setRowSelectionInterval(0, 0);
        panel.editorPanel().setAsSourceButton().doClick();

        clickRowAction(panel, 0, NodeRowAction.DELETE);

        assertFalse(panel.editorPanel().linkToHereButton().isEnabled());
        assertTrue(panel.editorPanel().linkStatus().getText().contains("链接源：未设置"));
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
        Rectangle cellRect = table.getCellRect(row, 3, true);
        MouseEvent event = new MouseEvent(
                table,
                MouseEvent.MOUSE_PRESSED,
                System.currentTimeMillis(),
                0,
                cellRect.x + 1,
                cellRect.y + 1,
                1,
                false);
        if (!table.editCellAt(row, 3, event)) {
            throw new AssertionError("Action column did not start editing");
        }
        Component component = table.getEditorComponent();
        ((NodeRowActionsPanel) component).button(action).doClick();
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
