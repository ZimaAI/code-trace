package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.Component;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import javax.swing.JLabel;
import javax.swing.JTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LinkedNodeTreeCellRendererTest {
    private JTree tree;

    @BeforeEach
    void setUp() {
        tree = new JTree();
    }

    @Test
    void showsTitleAndDisplayNameWhenTitleIsPresent() {
        TraceDocument doc = createDoc(List.of(
                new TraceNode("n1", "login()", "", "", "", 0, "", "", "", null, "认证")));
        TraceTreeModel model = new TraceTreeModel(() -> doc);
        tree.setModel(model);

        LinkedNodeTreeCellRenderer renderer = new LinkedNodeTreeCellRenderer(() -> doc, () -> null, () -> null);
        Component c = renderer.getTreeCellRendererComponent(tree, doc.nodes().get(0),
                false, false, true, 0, false);
        String text = ((JLabel) c).getText();
        assertTrue(text.startsWith("认证"));
        assertTrue(text.contains("login()"));
    }

    @Test
    void showsOnlyDisplayNameWhenTitleIsAbsent() {
        TraceDocument doc = createDoc(List.of(
                new TraceNode("n1", "login()", "", "", "", 0, "", "", "")));
        TraceTreeModel model = new TraceTreeModel(() -> doc);
        tree.setModel(model);

        LinkedNodeTreeCellRenderer renderer = new LinkedNodeTreeCellRenderer(() -> doc, () -> null, () -> null);
        Component c = renderer.getTreeCellRendererComponent(tree, doc.nodes().get(0),
                false, false, true, 0, false);
        assertEquals("login()", ((JLabel) c).getText());
    }

    @Test
    void showsFocusIconWhenNodeIsFocused() {
        TraceDocument doc = createDoc(List.of(
                new TraceNode("n1", "login()", "", "", "", 0, "", "", "")));
        TraceTreeModel model = new TraceTreeModel(() -> doc);
        tree.setModel(model);

        LinkedNodeTreeCellRenderer renderer = new LinkedNodeTreeCellRenderer(() -> doc, () -> "n1", () -> null);
        Component c = renderer.getTreeCellRendererComponent(tree, doc.nodes().get(0),
                false, false, true, 0, false);
        String text = ((JLabel) c).getText();
        assertTrue(text.startsWith(LinkedNodeTreeCellRenderer.FOCUS_PREFIX));
    }

    @Test
    void showsSourceRolePrefixForSourceNode() {
        TraceDocument doc = new TraceDocument(
                3, "t1", "T1", "", Instant.now(), Instant.now(),
                List.of(
                        new TraceNode("n1", "src", "", "", "", 0, "", "", ""),
                        new TraceNode("n2", "tgt", "", "", "", 0, "", "", "")),
                List.of(new TraceLink("l1", "n1", "n2", Instant.now(), TraceLinkKind.MANUAL)),
                Set.of());
        TraceTreeModel model = new TraceTreeModel(() -> doc);
        tree.setModel(model);

        LinkedNodeTreeCellRenderer renderer = new LinkedNodeTreeCellRenderer(() -> doc, () -> null, () -> null);
        Component c = renderer.getTreeCellRendererComponent(tree, doc.nodes().get(0),
                false, false, true, 0, false);
        assertTrue(((JLabel) c).getText().contains(LinkedNodeTreeCellRenderer.ROLE_SOURCE));
    }

    @Test
    void truncatesLongTitleWithEllipsis() {
        String longTitle = "这是一个非常非常非常非常长的标题文本";
        TraceDocument doc = createDoc(List.of(
                new TraceNode("n1", "login()", "", "", "", 0, "", "", "", null, longTitle)));
        TraceTreeModel model = new TraceTreeModel(() -> doc);
        tree.setModel(model);

        LinkedNodeTreeCellRenderer renderer = new LinkedNodeTreeCellRenderer(() -> doc, () -> null, () -> null);
        Component c = renderer.getTreeCellRendererComponent(tree, doc.nodes().get(0),
                false, false, true, 0, false);
        String text = ((JLabel) c).getText();
        assertTrue(text.contains("…"));
        assertTrue(text.length() < longTitle.length() + 10);
    }

    @Test
    void skipsDanglingLinkAndDoesNotApplyTargetRole() {
        // n1 is the only node; link points to n1 as target but source "deleted-node" doesn't exist
        TraceDocument doc = new TraceDocument(
                3, "t1", "T1", "", Instant.now(), Instant.now(),
                List.of(new TraceNode("n1", "remaining()", "", "", "", 0, "", "", "")),
                List.of(new TraceLink("l1", "deleted-node", "n1", Instant.now(), TraceLinkKind.MANUAL)),
                Set.of());
        TraceTreeModel model = new TraceTreeModel(() -> doc);
        tree.setModel(model);

        LinkedNodeTreeCellRenderer renderer = new LinkedNodeTreeCellRenderer(() -> doc, () -> null, () -> null);
        Component c = renderer.getTreeCellRendererComponent(tree, doc.nodes().get(0),
                false, false, true, 0, false);
        String text = ((JLabel) c).getText();
        // Should NOT show target role prefix because the link is dangling
        assertTrue(!text.contains(LinkedNodeTreeCellRenderer.ROLE_TARGET),
                "Dangling link should not produce TARGET role, got: " + text);
        assertEquals("remaining()", text);
    }

    private static TraceDocument createDoc(List<TraceNode> nodes) {
        return new TraceDocument(3, "t1", "T1", "", Instant.now(), Instant.now(),
                nodes, List.of(), Set.of());
    }
}
