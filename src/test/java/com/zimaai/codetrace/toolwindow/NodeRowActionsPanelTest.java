package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.zimaai.codetrace.model.TraceNode;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JButton;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;

class NodeRowActionsPanelTest {
    @Test
    void createsIconButtonsForEachRowAction() {
        NodeRowActionsPanel panel = new NodeRowActionsPanel(true, (action, node) -> {});

        for (NodeRowAction action : NodeRowAction.values()) {
            JButton button = panel.button(action);
            assertNotNull(button, action + " button should exist");
            assertNotNull(button.getIcon(), action + " button should have an icon");
            assertNotNull(button.getToolTipText(), action + " button should have a tooltip");
        }
    }

    @Test
    void clickPassesCurrentNodeAndAction() {
        AtomicReference<NodeRowAction> capturedAction = new AtomicReference<>();
        AtomicReference<TraceNode> capturedNode = new AtomicReference<>();
        TraceNode node = new TraceNode("node-1", "Node 1", "", "", "", 1, "JAVA", "", "");
        NodeRowActionsPanel panel = new NodeRowActionsPanel(true, (action, clickedNode) -> {
            capturedAction.set(action);
            capturedNode.set(clickedNode);
        });
        panel.setNode(node);

        panel.button(NodeRowAction.DELETE).doClick();

        assertEquals(NodeRowAction.DELETE, capturedAction.get());
        assertEquals(node, capturedNode.get());
    }

    @Test
    void editorReturnsCurrentNodeAsCellEditorValue() {
        AtomicReference<NodeRowAction> ignoredAction = new AtomicReference<>();
        TraceNode node = new TraceNode("node-1", "Node 1", "", "", "", 1, "JAVA", "", "");
        NodeRowActionsEditor editor = new NodeRowActionsEditor((action, clickedNode) -> ignoredAction.set(action));

        editor.getTableCellEditorComponent(new JTable(), node, true, 0, 3);

        assertEquals(node, editor.getCellEditorValue());
    }

    @Test
    void editorClearsNodeWhenValueIsNotTraceNode() {
        AtomicReference<TraceNode> capturedNode = new AtomicReference<>();
        TraceNode node = new TraceNode("node-1", "Node 1", "", "", "", 1, "JAVA", "", "");
        NodeRowActionsEditor editor = new NodeRowActionsEditor((action, clickedNode) -> capturedNode.set(clickedNode));
        JTable table = new JTable();

        editor.getTableCellEditorComponent(table, node, true, 0, 3);
        editor.getTableCellEditorComponent(table, null, true, 1, 3);
        java.awt.Component component = editor.getTableCellEditorComponent(table, null, true, 1, 3);

        ((NodeRowActionsPanel) component).button(NodeRowAction.DELETE).doClick();

        assertEquals(null, editor.getCellEditorValue());
        assertEquals(null, capturedNode.get());
    }

    @Test
    void rendererModeKeepsButtonsVisuallyEnabled() {
        NodeRowActionsPanel panel = new NodeRowActionsPanel(false, (action, node) -> {});

        for (NodeRowAction action : NodeRowAction.values()) {
            assertEquals(true, panel.button(action).isEnabled());
        }
    }
}
