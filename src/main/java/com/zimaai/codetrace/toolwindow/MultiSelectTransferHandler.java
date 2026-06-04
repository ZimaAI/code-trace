package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.tree.TreePath;

public final class MultiSelectTransferHandler extends TransferHandler {
    private static final DataFlavor FLAVOR = DataFlavor.stringFlavor;
    private static final String DELIMITER = ",";

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
        JTree tree = (JTree) c;
        TreePath[] paths = tree.getSelectionPaths();
        if (paths == null || paths.length == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paths.length; i++) {
            if (paths[i].getLastPathComponent() instanceof TraceNode node) {
                if (i > 0) {
                    sb.append(DELIMITER);
                }
                sb.append(node.id());
            }
        }

        return new StringSelection(sb.toString());
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return support.isDrop() && support.getComponent() instanceof JTree;
    }

    @Override
    public boolean importData(TransferSupport support) {
        JTree tree = (JTree) support.getComponent();
        JTree.DropLocation dropLocation = (JTree.DropLocation) support.getDropLocation();
        TreePath targetPath = dropLocation.getPath();

        if (targetPath == null || !(targetPath.getLastPathComponent() instanceof TraceNode targetNode)) {
            return false;
        }

        // 解析传输的数据
        String data;
        try {
            data = (String) support.getTransferable().getTransferData(FLAVOR);
        } catch (Exception e) {
            return false;
        }

        String[] nodeIds = data.split(DELIMITER);
        Set<String> sourceIds = new HashSet<>(Arrays.asList(nodeIds));

        // 验证：不能拖拽到自身或子节点
        TraceDocument doc = controller.state().currentDocument();
        for (String sourceId : sourceIds) {
            if (sourceId.equals(targetNode.id()) || isDescendantOf(targetNode, sourceId, doc)) {
                return false;
            }
        }

        // 执行移动
        String newParentId = targetNode.parentId();
        int childIndex = dropLocation.getChildIndex();

        // 在 EDT 线程中执行移动操作
        SwingUtilities.invokeLater(() -> {
            try {
                // 移动所有选中的节点
                int currentIndex = childIndex >= 0 ? childIndex : 0;
                for (String sourceId : sourceIds) {
                    if (childIndex < 0) {
                        controller.setParent(sourceId, newParentId);
                    } else {
                        controller.setParentAndIndex(sourceId, newParentId, currentIndex);
                        currentIndex++;
                    }
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
