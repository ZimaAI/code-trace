package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.event.EventListenerList;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

public final class TraceTreeModel implements TreeModel {
    static final TraceNode VIRTUAL_ROOT = new TraceNode(
            "__root__", "__root__", "", "", "", 0, "", "", "", null, null);

    private final Supplier<TraceDocument> documentSupplier;
    private final EventListenerList listeners = new EventListenerList();

    public TraceTreeModel(Supplier<TraceDocument> documentSupplier) {
        this.documentSupplier = Objects.requireNonNull(documentSupplier, "documentSupplier");
    }

    @Override
    public Object getRoot() {
        return VIRTUAL_ROOT;
    }

    @Override
    public Object getChild(Object parent, int index) {
        List<TraceNode> children = childrenOf(parent);
        return index >= 0 && index < children.size() ? children.get(index) : null;
    }

    @Override
    public int getChildCount(Object parent) {
        return childrenOf(parent).size();
    }

    @Override
    public boolean isLeaf(Object node) {
        return getChildCount(node) == 0;
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        if (!(child instanceof TraceNode childNode)) return -1;
        List<TraceNode> children = childrenOf(parent);
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).id().equals(childNode.id())) return i;
        }
        return -1;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        // Not supported — nodes are immutable records
    }

    @Override
    public void addTreeModelListener(TreeModelListener listener) {
        listeners.add(TreeModelListener.class, listener);
    }

    @Override
    public void removeTreeModelListener(TreeModelListener listener) {
        listeners.remove(TreeModelListener.class, listener);
    }

    public void fireStructureChanged() {
        TreeModelEvent e = new TreeModelEvent(this, new Object[]{VIRTUAL_ROOT});
        for (TreeModelListener l : listeners.getListeners(TreeModelListener.class)) {
            l.treeStructureChanged(e);
        }
    }

    private List<TraceNode> childrenOf(Object parent) {
        TraceDocument doc = documentSupplier.get();
        if (doc == null) return List.of();
        if (parent == VIRTUAL_ROOT) {
            return doc.nodes().stream()
                    .filter(n -> n.parentId() == null)
                    .toList();
        }
        if (parent instanceof TraceNode parentNode) {
            return doc.nodes().stream()
                    .filter(n -> Objects.equals(n.parentId(), parentNode.id()))
                    .toList();
        }
        return List.of();
    }
}
