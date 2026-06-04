package com.zimaai.codetrace.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import com.zimaai.codetrace.model.TraceNode;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

/**
 * 折叠指示器渲染器，在编号列显示展开/折叠图标和树形连接线
 */
public class CollapseIndicatorRenderer extends DefaultTableCellRenderer {
    private final FilteredNodeTableModel filteredModel;
    private final java.util.function.Function<String, Boolean> isExpandedFunction;

    public CollapseIndicatorRenderer(FilteredNodeTableModel filteredModel,
                                    java.util.function.Function<String, Boolean> isExpandedFunction) {
        this.filteredModel = filteredModel;
        this.isExpandedFunction = isExpandedFunction;
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (c instanceof JLabel label && row >= 0 && row < filteredModel.getRowCount()) {
            TraceNode node = filteredModel.getNodeAt(row);
            boolean hasChildren = filteredModel.getSourceModel().hasChildren(node.id());
            boolean isExpanded = isExpandedFunction.apply(node.id());
            int depth = filteredModel.getNodeDepth(node);
            boolean isLastChild = filteredModel.isLastChild(node);

            // 构建树形前缀
            String treePrefix = buildTreePrefix(node, depth, hasChildren, isExpanded, isLastChild);

            // 设置文本
            String number = value != null ? value.toString() : "";
            label.setText(treePrefix + " " + number);

            // 设置图标
            if (hasChildren) {
                label.setIcon(isExpanded ? AllIcons.General.ArrowDown : AllIcons.General.ArrowRight);
            } else {
                label.setIcon(null);
            }

            // 设置缩进
            int indent = depth * 20;
            label.setBorder(BorderFactory.createEmptyBorder(0, indent + 5, 0, 0));
        }

        return c;
    }

    /**
     * 构建树形前缀字符串
     */
    public static String buildTreePrefix(TraceNode node, int depth, boolean hasChildren,
                                         boolean isExpanded, boolean isLastChild) {
        if (depth == 0) {
            // 根节点
            return hasChildren ? (isExpanded ? "▼" : "▶") : " ";
        }

        StringBuilder prefix = new StringBuilder();

        // 添加祖先延续线
        for (int i = 0; i < depth - 1; i++) {
            prefix.append("│ ");
        }

        // 添加当前节点连接线
        if (isLastChild) {
            prefix.append("└──");
        } else {
            prefix.append("├──");
        }

        return prefix.toString();
    }
}
