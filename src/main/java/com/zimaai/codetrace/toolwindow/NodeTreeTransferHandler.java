package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.TransferHandler;
import javax.swing.tree.TreePath;

public final class NodeTreeTransferHandler extends TransferHandler {
    private static final int INDENT_THRESHOLD = 20;

    private final CodeTraceController controller;
    private final Runnable refreshUi;

    public NodeTreeTransferHandler(CodeTraceController controller, Runnable refreshUi) {
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
        TreePath path = tree.getSelectionPath();
        if (path == null || !(path.getLastPathComponent() instanceof TraceNode node)) {
            return null;
        }
        return new StringSelection(node.id());
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

        TreePath sourcePath = tree.getSelectionPath();
        if (sourcePath == null || !(sourcePath.getLastPathComponent() instanceof TraceNode sourceNode)) {
            return false;
        }

        // Detect indent: compare drop X with target row X
        Point dropPoint = support.getDropLocation().getDropPoint();
        int row = tree.getRowForPath(targetPath);
        Rectangle rowBounds = tree.getRowBounds(row);
        if (rowBounds != null && dropPoint != null) {
            int deltaX = dropPoint.x - rowBounds.x;
            if (deltaX > INDENT_THRESHOLD && !isDescendantOf(targetNode, sourceNode)) {
                // Reparent: make source a child of target
                controller.setParent(sourceNode.id(), targetNode.id());
                refreshUi.run();
                return true;
            }
        }

        // Otherwise, reorder within same parent
        String newParentId = targetNode.parentId();
        int childIndex = dropLocation.getChildIndex();
        if (childIndex < 0) {
            // Dropped on the target node -> reorder after it within same parent
            controller.setParent(sourceNode.id(), newParentId);
        } else {
            controller.setParentAndIndex(sourceNode.id(), newParentId, childIndex);
        }
        refreshUi.run();
        return true;
    }

    private boolean isDescendantOf(TraceNode ancestor, TraceNode node) {
        String parentId = ancestor.parentId();
        while (parentId != null) {
            if (parentId.equals(node.id())) return true;
            TraceDocument doc = controller.state().currentDocument();
            if (doc == null) break;
            String finalParentId = parentId;
            TraceNode parent = doc.nodes().stream()
                    .filter(n -> n.id().equals(finalParentId))
                    .findFirst().orElse(null);
            if (parent == null) break;
            parentId = parent.parentId();
        }
        return false;
    }
}
