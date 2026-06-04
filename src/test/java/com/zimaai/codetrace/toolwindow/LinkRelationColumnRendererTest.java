package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.Component;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JLabel;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;

class LinkRelationColumnRendererTest {

    @Test
    void render_shouldShowEmptyForNodeWithNoLinks() {
        // Given
        TraceNode node = new TraceNode("node-1", "TestNode", "", "", "", 0, "", "", "");
        TraceDocument doc = createDocument(List.of(node), List.of());
        Map<String, String> numbers = Map.of("node-1", "1");

        LinkRelationColumnRenderer renderer = new LinkRelationColumnRenderer(
                () -> doc, () -> numbers, () -> null, () -> null);

        // When
        Component component = renderer.getTableCellRendererComponent(
                new JTable(), node, false, false, 0, 1);

        // Then
        JLabel label = (JLabel) component;
        assertEquals("", label.getText());
    }

    @Test
    void render_shouldShowIncomingAndOutgoingLinks() {
        // Given
        TraceNode nodeA = new TraceNode("node-a", "A", "", "", "", 0, "", "", "");
        TraceNode nodeB = new TraceNode("node-b", "B", "", "", "", 0, "", "", "");
        TraceNode nodeC = new TraceNode("node-c", "C", "", "", "", 0, "", "", "");
        TraceLink link1 = new TraceLink("link-1", "node-a", "node-b", Instant.now(), TraceLinkKind.MANUAL);
        TraceLink link2 = new TraceLink("link-2", "node-b", "node-c", Instant.now(), TraceLinkKind.MANUAL);

        TraceDocument doc = createDocument(List.of(nodeA, nodeB, nodeC), List.of(link1, link2));
        Map<String, String> numbers = Map.of("node-a", "1", "node-b", "2", "node-c", "3");

        LinkRelationColumnRenderer renderer = new LinkRelationColumnRenderer(
                () -> doc, () -> numbers, () -> null, () -> null);

        // When
        Component component = renderer.getTableCellRendererComponent(
                new JTable(), nodeB, false, false, 0, 1);

        // Then
        JLabel label = (JLabel) component;
        assertEquals("←1 →3", label.getText());
    }

    @Test
    void render_shouldShowOnlyIncomingLinks() {
        // Given
        TraceNode nodeA = new TraceNode("node-a", "A", "", "", "", 0, "", "", "");
        TraceNode nodeB = new TraceNode("node-b", "B", "", "", "", 0, "", "", "");
        TraceLink link = new TraceLink("link-1", "node-a", "node-b", Instant.now(), TraceLinkKind.MANUAL);

        TraceDocument doc = createDocument(List.of(nodeA, nodeB), List.of(link));
        Map<String, String> numbers = Map.of("node-a", "1", "node-b", "2");

        LinkRelationColumnRenderer renderer = new LinkRelationColumnRenderer(
                () -> doc, () -> numbers, () -> null, () -> null);

        // When
        Component component = renderer.getTableCellRendererComponent(
                new JTable(), nodeB, false, false, 0, 1);

        // Then
        JLabel label = (JLabel) component;
        assertEquals("←1", label.getText());
    }

    @Test
    void render_shouldShowOnlyOutgoingLinks() {
        // Given
        TraceNode nodeA = new TraceNode("node-a", "A", "", "", "", 0, "", "", "");
        TraceNode nodeB = new TraceNode("node-b", "B", "", "", "", 0, "", "", "");
        TraceLink link = new TraceLink("link-1", "node-a", "node-b", Instant.now(), TraceLinkKind.MANUAL);

        TraceDocument doc = createDocument(List.of(nodeA, nodeB), List.of(link));
        Map<String, String> numbers = Map.of("node-a", "1", "node-b", "2");

        LinkRelationColumnRenderer renderer = new LinkRelationColumnRenderer(
                () -> doc, () -> numbers, () -> null, () -> null);

        // When
        Component component = renderer.getTableCellRendererComponent(
                new JTable(), nodeA, false, false, 0, 1);

        // Then
        JLabel label = (JLabel) component;
        assertEquals("→2", label.getText());
    }

    @Test
    void render_shouldSkipDanglingLinks() {
        // Given
        TraceNode nodeA = new TraceNode("node-a", "A", "", "", "", 0, "", "", "");
        TraceNode nodeB = new TraceNode("node-b", "B", "", "", "", 0, "", "", "");
        TraceLink link = new TraceLink("link-1", "node-a", "node-b", Instant.now(), TraceLinkKind.MANUAL);

        // 只包含 nodeB，不包含 nodeA（模拟 nodeA 已删除）
        TraceDocument doc = createDocument(List.of(nodeB), List.of(link));
        Map<String, String> numbers = Map.of("node-b", "1");

        LinkRelationColumnRenderer renderer = new LinkRelationColumnRenderer(
                () -> doc, () -> numbers, () -> null, () -> null);

        // When
        Component component = renderer.getTableCellRendererComponent(
                new JTable(), nodeB, false, false, 0, 1);

        // Then
        JLabel label = (JLabel) component;
        assertEquals("", label.getText());
    }

    private static TraceDocument createDocument(List<TraceNode> nodes, List<TraceLink> links) {
        return new TraceDocument(3, "doc-1", "Test Doc", "", Instant.now(), Instant.now(),
                nodes, links, Set.of());
    }
}
