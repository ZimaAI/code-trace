package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
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

        // Validate and capture all data synchronously; defer state mutation
        // and UI refresh so Swing DnD cleanup completes before the model changes.
        // Otherwise IntelliJ's CachingTreePath will try to resolve a stale child
        // index against the already-updated model.
        String sourceId = sourceNode.id();
        TraceDocument doc = controller.state().currentDocument();

        // Detect indent: compare drop X with target row X
        Point dropPoint = support.getDropLocation().getDropPoint();
        int row = tree.getRowForPath(targetPath);
        Rectangle rowBounds = tree.getRowBounds(row);
        if (rowBounds != null && dropPoint != null) {
            int deltaX = dropPoint.x - rowBounds.x;
            if (deltaX > INDENT_THRESHOLD
                    && !sourceId.equals(targetNode.id())
                    && !isDescendantOf(targetNode, sourceNode, doc)) {
                // Reparent: make source a child of target.
                String targetId = targetNode.id();
                SwingUtilities.invokeLater(() -> {
                    try {
                        controller.setParent(sourceId, targetId);
                    } catch (IllegalArgumentException ignored) {
                    }
                    refreshUi.run();
                });
                return true;
            }
        }

        // Otherwise, reorder within same parent
        String newParentId = targetNode.parentId();
        // Guard: a node cannot be its own parent, and the move must not create a cycle.
        if (sourceId.equals(newParentId)
                || isDescendantOf(targetNode, sourceNode, doc)) {
            return false;
        }
        int childIndex = dropLocation.getChildIndex();
        SwingUtilities.invokeLater(() -> {
            try {
                if (childIndex < 0) {
                    controller.setParent(sourceId, newParentId);
                } else {
                    controller.setParentAndIndex(sourceId, newParentId, childIndex);
                }
            } catch (IllegalArgumentException ignored) {
            }
            refreshUi.run();
        });
        return true;
    }

    private static boolean isDescendantOf(TraceNode node, TraceNode potentialAncestor, TraceDocument doc) {
        if (doc == null) return false;
        Set<String> visited = new HashSet<>();
        String parentId = node.parentId();
        while (parentId != null) {
            if (!visited.add(parentId)) return false; // safety: cycle detected in existing data
            if (parentId.equals(potentialAncestor.id())) return true;
            String currentId = parentId;
            TraceNode parent = doc.nodes().stream()
                    .filter(n -> n.id().equals(currentId))
                    .findFirst().orElse(null);
            parentId = parent == null ? null : parent.parentId();
        }
        return false;
    }
}
