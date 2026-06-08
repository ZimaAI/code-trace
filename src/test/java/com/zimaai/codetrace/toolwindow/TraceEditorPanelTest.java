package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.intellij.ui.JBColor;
import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.LayoutManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class TraceEditorPanelTest {
    @Test
    void enablesSoftWrapForNodeNoteEditing() {
        TraceEditorPanel panel = new TraceEditorPanel();

        assertTrue(panel.nodeNote().getLineWrap());
        assertTrue(panel.nodeNote().getWrapStyleWord());
    }

    @Test
    void appliesThemeAwareSelectionColorsForNodeNote() {
        TraceEditorPanel panel = new TraceEditorPanel();

        assertEquals(JBColor.class, panel.nodeNote().getSelectionColor().getClass());
        assertEquals(JBColor.class, panel.nodeNote().getSelectedTextColor().getClass());
        assertEquals(JBColor.class, panel.nodeNote().getCaretColor().getClass());
    }

    @Test
    void usesWrapLayoutForNodeToolbarAndDisablesJumpButtonByDefault() {
        TraceEditorPanel panel = new TraceEditorPanel();

        LayoutManager layout = panel.nodeToolbar().getLayout();
        assertEquals(WrapLayout.class, layout.getClass());

        JButton goToLinked = panel.goToLinkedButton();
        assertEquals("Go to Linked Node", goToLinked.getText());
        assertFalse(goToLinked.isEnabled());
    }

    @Test
    void nodeToolbarContainsOnlyLinkAndViewActions() {
        TraceEditorPanel panel = new TraceEditorPanel();
        JPanel toolbar = panel.nodeToolbar();

        assertFalse(toolbarContainsButtonText(toolbar, "Edit Node"));
        assertFalse(toolbarContainsButtonText(toolbar, "Delete Node"));
        assertFalse(toolbarContainsButtonText(toolbar, "Move Up"));
        assertFalse(toolbarContainsButtonText(toolbar, "Move Down"));
        assertTrue(toolbarContainsButtonText(toolbar, "Set as Source"));
        assertTrue(toolbarContainsButtonText(toolbar, "Link To Here"));
        assertTrue(toolbarContainsButtonText(toolbar, "Unlink"));
        assertTrue(toolbarContainsButtonText(toolbar, "Go to Linked Node"));
        assertTrue(toolbarContainsButtonText(toolbar, "Expand All"));
        assertTrue(toolbarContainsButtonText(toolbar, "Collapse All"));
    }

    @Test
    void exposesSeparateNodeMainAndNotesPanels() {
        TraceEditorPanel panel = new TraceEditorPanel();

        assertNotNull(panel.nodeMainPanel());
        assertNotNull(panel.notesPanel());
        assertTrue(panel.nodeMainPanel().isAncestorOf(panel.nodeTable()));
        assertTrue(panel.notesPanel().isAncestorOf(panel.traceNote()));
        assertTrue(panel.notesPanel().isAncestorOf(panel.nodeNote()));
    }

    @Test
    void setsIconsOnAllToolbarButtons() {
        TraceEditorPanel panel = new TraceEditorPanel();

        assertNotNull(panel.editNodeButton().getIcon(), "Edit Node button should have an icon");
        assertNotNull(panel.deleteNodeButton().getIcon(), "Delete Node button should have an icon");
        assertNotNull(panel.moveUpButton().getIcon(), "Move Up button should have an icon");
        assertNotNull(panel.moveDownButton().getIcon(), "Move Down button should have an icon");
        assertNotNull(panel.setAsSourceButton().getIcon(), "Set as Source button should have an icon");
        assertNotNull(panel.linkToHereButton().getIcon(), "Link To Here button should have an icon");
        assertNotNull(panel.unlinkButton().getIcon(), "Unlink button should have an icon");
        assertNotNull(panel.goToLinkedButton().getIcon(), "Go To Linked button should have an icon");
        assertNotNull(panel.expandAllButton().getIcon(), "Expand All button should have an icon");
        assertNotNull(panel.collapseAllButton().getIcon(), "Collapse All button should have an icon");
        assertNotNull(panel.saveTraceNoteButton().getIcon(), "Save Trace Note button should have an icon");
        assertNotNull(panel.saveNodeNoteButton().getIcon(), "Save Node Note button should have an icon");
    }

    @Test
    void setsTooltipsOnAllToolbarButtons() {
        TraceEditorPanel panel = new TraceEditorPanel();

        assertNotNull(panel.editNodeButton().getToolTipText(), "Edit Node button should have a tooltip");
        assertNotNull(panel.deleteNodeButton().getToolTipText(), "Delete Node button should have a tooltip");
        assertNotNull(panel.moveUpButton().getToolTipText(), "Move Up button should have a tooltip");
        assertNotNull(panel.moveDownButton().getToolTipText(), "Move Down button should have a tooltip");
        assertNotNull(panel.setAsSourceButton().getToolTipText(), "Set as Source button should have a tooltip");
        assertNotNull(panel.linkToHereButton().getToolTipText(), "Link To Here button should have a tooltip");
        assertNotNull(panel.unlinkButton().getToolTipText(), "Unlink button should have a tooltip");
        assertNotNull(panel.goToLinkedButton().getToolTipText(), "Go To Linked button should have a tooltip");
        assertNotNull(panel.expandAllButton().getToolTipText(), "Expand All button should have a tooltip");
        assertNotNull(panel.collapseAllButton().getToolTipText(), "Collapse All button should have a tooltip");
        assertNotNull(panel.saveTraceNoteButton().getToolTipText(), "Save Trace Note button should have a tooltip");
        assertNotNull(panel.saveNodeNoteButton().getToolTipText(), "Save Node Note button should have a tooltip");
    }

    @Test
    void exposesJTableInsteadOfJTreeAfterMigration() {
        TraceEditorPanel panel = new TraceEditorPanel();

        assertNotNull(panel.nodeTable());
    }

    @Test
    void testToolbarContainsExpandCollapseButtons() {
        TraceEditorPanel panel = new TraceEditorPanel();

        // Check toolbar contains expand/collapse buttons
        JPanel toolbar = panel.nodeToolbar();
        java.util.List<java.awt.Component> components = java.util.Arrays.asList(toolbar.getComponents());

        assertTrue(components.contains(panel.expandAllButton()), "Toolbar should contain the Expand All button");
        assertTrue(components.contains(panel.collapseAllButton()), "Toolbar should contain the Collapse All button");
    }

    @Test
    void testConfigureTableSetsFilteredModel() {
        TraceEditorPanel panel = new TraceEditorPanel();
        NodeTableModel sourceModel = createTestSourceModel();
        TraceDocument document = createTestDocument();

        panel.configureTableWithCollapseSupport(sourceModel, document);

        assertNotNull(panel.getFilteredModel());
        assertTrue(panel.nodeTable().getModel() instanceof FilteredNodeTableModel);
    }

    private static boolean toolbarContainsButtonText(JPanel toolbar, String text) {
        for (java.awt.Component component : toolbar.getComponents()) {
            if (component instanceof JButton button && text.equals(button.getText())) {
                return true;
            }
        }
        return false;
    }

    private NodeTableModel createTestSourceModel() {
        List<TraceNode> nodes = List.of(
            new TraceNode("1", "Root1", null, null, null, 0, null, null, null, null, null),
            new TraceNode("2", "Child1", null, null, null, 0, null, null, null, "1", null)
        );
        Map<String, String> numberMap = Map.of("1", "1", "2", "1.1");
        return new NodeTableModel(nodes, numberMap, List.of());
    }

    private TraceDocument createTestDocument() {
        List<TraceNode> nodes = List.of(
            new TraceNode("1", "Root1", null, null, null, 0, null, null, null, null, null),
            new TraceNode("2", "Child1", null, null, null, 0, null, null, null, "1", null)
        );
        return new TraceDocument(3, "test", "test", "test", Instant.now(), Instant.now(), nodes, List.of(), Set.of());
    }
}
