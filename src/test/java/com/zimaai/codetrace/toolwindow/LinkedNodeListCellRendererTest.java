package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import com.intellij.ui.JBColor;
import java.awt.Component;
import java.time.Instant;
import java.util.List;
import javax.swing.JList;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;

class LinkedNodeListCellRendererTest {
    @Test
    void keepsRowTextEqualToDisplayNameAndUsesDifferentDecorationForRoles() {
        TraceNode source = new TraceNode("node-1", "source line", "A#a", "a()", "A.java", 1, "JAVA", "", "A#a");
        TraceNode target = new TraceNode("node-2", "target line", "B#b", "b()", "B.java", 2, "JAVA", "", "B#b");
        TraceDocument document = new TraceDocument(
                2,
                "trace-1",
                "Trace 1",
                "",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(source, target),
                List.of(new TraceLink("link-1", "node-1", "node-2", Instant.parse("2026-05-29T10:01:00Z"), TraceLinkKind.MANUAL)));

        LinkedNodeListCellRenderer renderer = new LinkedNodeListCellRenderer(() -> document, () -> null);
        JList<TraceNode> list = new JList<>(new TraceNode[]{source, target});

        Component sourceComponent = renderer.getListCellRendererComponent(list, source, 0, false, false);
        assertEquals("source line", ((JLabel) sourceComponent).getText());
        java.awt.Color sourceColor = sourceComponent.getBackground();

        Component targetComponent = renderer.getListCellRendererComponent(list, target, 1, false, false);
        assertEquals("target line", ((JLabel) targetComponent).getText());
        java.awt.Color targetColor = targetComponent.getBackground();

        assertNotNull(sourceColor);
        assertNotNull(targetColor);
        assertNotEquals(sourceColor.getRGB(), targetColor.getRGB());
        assertEquals(JBColor.class, sourceColor.getClass());
        assertEquals(JBColor.class, targetColor.getClass());
    }

    @Test
    void exposesThemeAwareColorsForPendingSourceSourceAndTarget() {
        assertEquals(JBColor.class, LinkedNodeListCellRenderer.PENDING_SOURCE_COLOR.getClass());
        assertEquals(JBColor.class, LinkedNodeListCellRenderer.SOURCE_COLOR.getClass());
        assertEquals(JBColor.class, LinkedNodeListCellRenderer.TARGET_COLOR.getClass());
        assertNotEquals(
                LinkedNodeListCellRenderer.PENDING_SOURCE_COLOR.getRGB(),
                LinkedNodeListCellRenderer.SOURCE_COLOR.getRGB());
        assertNotEquals(
                LinkedNodeListCellRenderer.SOURCE_COLOR.getRGB(),
                LinkedNodeListCellRenderer.TARGET_COLOR.getRGB());
    }
}
