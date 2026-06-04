package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

public final class LinkRelationColumnRenderer extends DefaultTableCellRenderer {
    private final Supplier<TraceDocument> documentSupplier;
    private final Supplier<Map<String, String>> numberMapSupplier;
    private final Supplier<String> focusedNodeIdSupplier;
    private final Supplier<String> pendingSourceSupplier;

    public LinkRelationColumnRenderer(
            Supplier<TraceDocument> documentSupplier,
            Supplier<Map<String, String>> numberMapSupplier,
            Supplier<String> focusedNodeIdSupplier,
            Supplier<String> pendingSourceSupplier) {
        this.documentSupplier = documentSupplier;
        this.numberMapSupplier = numberMapSupplier;
        this.focusedNodeIdSupplier = focusedNodeIdSupplier;
        this.pendingSourceSupplier = pendingSourceSupplier;
    }

    @Override
    public Component getTableCellRendererComponent(
            JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {

        JLabel label = (JLabel) super.getTableCellRendererComponent(
                table, "", isSelected, hasFocus, row, column);

        if (!(value instanceof TraceNode node) || node == TraceTreeModel.VIRTUAL_ROOT) {
            return label;
        }

        TraceDocument document = documentSupplier.get();
        Map<String, String> numberMap = numberMapSupplier.get();

        if (document == null || numberMap == null) {
            return label;
        }

        // 计算链接关系
        List<String> incomingNumbers = new ArrayList<>();
        List<String> outgoingNumbers = new ArrayList<>();

        // 预构建 nodeIds 集合用于悬空链接检查
        Set<String> nodeIds = document.nodes().stream()
                .map(TraceNode::id)
                .collect(java.util.stream.Collectors.toSet());

        for (TraceLink link : document.links()) {
            // 跳过悬空链接
            if (!nodeIds.contains(link.sourceNodeId()) || !nodeIds.contains(link.targetNodeId())) {
                continue;
            }

            if (node.id().equals(link.targetNodeId())) {
                // 当前节点是目标，来源节点编号
                String sourceNumber = numberMap.get(link.sourceNodeId());
                if (sourceNumber != null) {
                    incomingNumbers.add(sourceNumber);
                }
            }
            if (node.id().equals(link.sourceNodeId())) {
                // 当前节点是来源，目标节点编号
                String targetNumber = numberMap.get(link.targetNodeId());
                if (targetNumber != null) {
                    outgoingNumbers.add(targetNumber);
                }
            }
        }

        // 构建紧凑格式字符串
        StringBuilder sb = new StringBuilder();
        if (!incomingNumbers.isEmpty()) {
            sb.append("←").append(String.join(",", incomingNumbers));
        }
        if (!outgoingNumbers.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append("→").append(String.join(",", outgoingNumbers));
        }

        label.setText(sb.toString());
        return label;
    }
}
