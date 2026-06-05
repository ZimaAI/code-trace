package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.table.TableColumnModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeTracePanelColumnWidthTest {
    @TempDir
    Path tempDir;

    @Test
    void keepsUserAdjustedColumnWidthsAfterCollapseAll() {
        CodeTracePanel panel = panelFor(documentWithManyExpandedChildren());
        panel.getComponent().setSize(1200, 300);
        layoutRecursively(panel.getComponent());

        JTable table = panel.editorPanel().nodeTable();
        TableColumnModel columns = table.getColumnModel();

        columns.getColumn(0).setPreferredWidth(120);
        columns.getColumn(0).setWidth(120);
        columns.getColumn(1).setPreferredWidth(700);
        columns.getColumn(1).setWidth(700);
        layoutRecursively(panel.getComponent());

        int beforeNumberWidth = columns.getColumn(0).getWidth();
        int beforeNameWidth = columns.getColumn(1).getWidth();
        int beforeLinkWidth = columns.getColumn(2).getWidth();

        JButton collapseAll = panel.editorPanel().collapseAllButton();
        collapseAll.doClick();
        layoutRecursively(panel.getComponent());

        int viewportWidth = ((JViewport) table.getParent()).getWidth();
        assertEquals(beforeNumberWidth, savedColumnWidths(panel)[0]);
        assertEquals(beforeNameWidth, savedColumnWidths(panel)[1]);
        assertEquals(beforeNumberWidth, table.getColumnModel().getColumn(0).getWidth());
        assertEquals(beforeNameWidth, table.getColumnModel().getColumn(1).getWidth());
        assertEquals(viewportWidth - beforeNumberWidth - beforeNameWidth, table.getColumnModel().getColumn(2).getWidth());
    }

    @Test
    void stretchesLastColumnToFillViewportWidth() {
        CodeTracePanel panel = panelFor(documentWithManyExpandedChildren());
        panel.getComponent().setSize(1200, 300);
        layoutRecursively(panel.getComponent());

        JTable table = panel.editorPanel().nodeTable();
        TableColumnModel columns = table.getColumnModel();

        columns.getColumn(0).setPreferredWidth(120);
        columns.getColumn(0).setWidth(120);
        columns.getColumn(1).setPreferredWidth(300);
        columns.getColumn(1).setWidth(300);
        layoutRecursively(panel.getComponent());

        int viewportWidth = ((JViewport) table.getParent()).getWidth();
        int actualTotalWidth = columns.getColumn(0).getWidth()
                + columns.getColumn(1).getWidth()
                + columns.getColumn(2).getWidth();

        assertEquals(viewportWidth, actualTotalWidth);
        assertFalse(columns.getColumn(2).getResizable());
    }

    private CodeTracePanel panelFor(TraceDocument document) {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", document);
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        CodeTracePanel panel = new CodeTracePanel(controller);
        panel.reloadFromDisk();
        return panel;
    }

    private static TraceDocument documentWithManyExpandedChildren() {
        List<TraceNode> nodes = new ArrayList<>();
        nodes.add(new TraceNode("root", "Root", "A#root", "root()", "A.java", 10, "JAVA", "", "A#root"));
        for (int i = 0; i < 20; i++) {
            nodes.add(new TraceNode(
                    "child-" + i,
                    "Child " + i + " with a longer label to increase row width variance",
                    "A#child" + i,
                    "child" + i + "()",
                    "A.java",
                    20 + i,
                    "JAVA",
                    "",
                    "A#child" + i,
                    "root",
                    null));
        }
        return new TraceDocument(
                3,
                "trace-1",
                "Trace 1",
                "",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                nodes,
                List.of(),
                Set.of("root"));
    }

    private static void layoutRecursively(Component component) {
        component.doLayout();
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                layoutRecursively(child);
            }
        }
    }

    private static int[] savedColumnWidths(CodeTracePanel panel) {
        try {
            Field field = CodeTracePanel.class.getDeclaredField("savedColumnWidths");
            field.setAccessible(true);
            return (int[]) field.get(panel);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
