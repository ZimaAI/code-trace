package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

public final class MultiSelectTransferHandler extends TransferHandler {
    private static final DataFlavor FLAVOR = DataFlavor.stringFlavor;
    private static final String DELIMITER = ",";
    private static final int INDENT_THRESHOLD = 20;

    private final CodeTraceController controller;
    private final Runnable refreshUi;

    public MultiSelectTransferHandler(CodeTraceController controller, Runnable refreshUi) {
        this.controller = controller;
        this.refreshUi = refreshUi;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        JTable table = (JTable) c;
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows == null || selectedRows.length == 0) {
            return null;
        }

        NodeTableModel model = (NodeTableModel) table.getModel();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < selectedRows.length; i++) {
            TraceNode node = model.getNodeAt(selectedRows[i]);
            if (i > 0) {
                sb.append(DELIMITER);
            }
            sb.append(node.id());
        }

        return new StringSelection(sb.toString());
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return support.isDrop() && support.getComponent() instanceof JTable;
    }

    @Override
    public boolean importData(TransferSupport support) {
        JTable table = (JTable) support.getComponent();
        JTable.DropLocation dropLocation = (JTable.DropLocation) support.getDropLocation();

        // 解析传输的数据
        String data;
        try {
            data = (String) support.getTransferable().getTransferData(FLAVOR);
        } catch (Exception e) {
            return false;
        }

        String[] nodeIds = data.split(DELIMITER);
        Set<String> sourceIds = new HashSet<>(Arrays.asList(nodeIds));

        // 获取目标行
        int targetRow = dropLocation.getRow();
        NodeTableModel model = (NodeTableModel) table.getModel();

        // 如果拖拽到表格底部（空行），targetRow 可能等于 rowCount
        if (targetRow < 0 || targetRow > model.getRowCount()) {
            return false;
        }

        // 确定目标节点和新父节点
        TraceNode targetNode;
        String newParentId;
        int insertIndex;

        if (targetRow >= model.getRowCount()) {
            // 拖拽到表格底部：成为根节点
            targetNode = null;
            newParentId = null;
            insertIndex = model.getRowCount();
        } else {
            targetNode = model.getNodeAt(targetRow);

            // 检查拖拽位置：如果在目标节点左侧较远，成为目标节点的子节点
            // 否则成为目标节点的兄弟节点
            Point dropPoint = dropLocation.getDropPoint();
            if (dropPoint != null) {
                java.awt.Rectangle cellRect = table.getCellRect(targetRow, 0, true);
                int deltaX = dropPoint.x - cellRect.x;

                if (deltaX > INDENT_THRESHOLD) {
                    // 成为目标节点的子节点
                    newParentId = targetNode.id();
                    insertIndex = 0; // 插入为第一个子节点
                } else {
                    // 成为目标节点的兄弟节点
                    newParentId = targetNode.parentId();
                    insertIndex = targetRow;
                }
            } else {
                // 默认成为兄弟节点
                newParentId = targetNode.parentId();
                insertIndex = targetRow;
            }
        }

        // 验证：不能拖拽到自身或子节点
        TraceDocument doc = controller.state().currentDocument();
        for (String sourceId : sourceIds) {
            if (targetNode != null && (sourceId.equals(targetNode.id()) || isDescendantOf(targetNode, sourceId, doc))) {
                return false;
            }
        }

        // 在 EDT 线程中执行移动操作
        SwingUtilities.invokeLater(() -> {
            try {
                // 移动所有选中的节点
                int currentIndex = insertIndex;
                for (String sourceId : sourceIds) {
                    controller.setParentAndIndex(sourceId, newParentId, currentIndex);
                    currentIndex++;
                }
            } catch (IllegalArgumentException ignored) {
            }
            refreshUi.run();
        });

        return true;
    }

    private static boolean isDescendantOf(TraceNode node, String potentialAncestorId, TraceDocument doc) {
        if (doc == null) return false;
        Set<String> visited = new HashSet<>();
        String currentParentId = node.parentId();
        while (currentParentId != null) {
            if (!visited.add(currentParentId)) return false;
            if (currentParentId.equals(potentialAncestorId)) return true;
            String id = currentParentId;
            TraceNode parent = doc.nodes().stream()
                    .filter(n -> n.id().equals(id))
                    .findFirst().orElse(null);
            currentParentId = parent == null ? null : parent.parentId();
        }
        return false;
    }
}
