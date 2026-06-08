package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceNode;
import java.awt.Component;
import java.util.function.BiConsumer;
import javax.swing.AbstractCellEditor;
import javax.swing.JTable;
import javax.swing.table.TableCellEditor;

final class NodeRowActionsEditor extends AbstractCellEditor implements TableCellEditor {
    private final NodeRowActionsPanel panel;
    private TraceNode currentNode;

    NodeRowActionsEditor(BiConsumer<NodeRowAction, TraceNode> actionHandler) {
        panel = new NodeRowActionsPanel(true, (action, node) -> {
            stopCellEditing();
            actionHandler.accept(action, node);
        });
    }

    @Override
    public Object getCellEditorValue() {
        return currentNode;
    }

    @Override
    public Component getTableCellEditorComponent(
            JTable table, Object value, boolean isSelected, int row, int column) {
        currentNode = value instanceof TraceNode node ? node : null;
        panel.setNode(currentNode);
        panel.setBackground(table.getSelectionBackground());
        return panel;
    }
}
