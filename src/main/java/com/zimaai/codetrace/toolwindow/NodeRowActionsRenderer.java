package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceNode;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.TableCellRenderer;

final class NodeRowActionsRenderer implements TableCellRenderer {
    private final NodeRowActionsPanel panel = new NodeRowActionsPanel(false, (action, node) -> {});

    @Override
    public Component getTableCellRendererComponent(
            JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
        if (value instanceof TraceNode node) {
            panel.setNode(node);
        }
        panel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
        return panel;
    }
}
