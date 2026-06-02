package com.zimaai.codetrace.toolwindow;

import com.intellij.ui.components.JBList;
import com.zimaai.codetrace.model.TraceNode;
import javax.swing.JList;
import javax.swing.TransferHandler;

public final class NodeListReorderTransferHandler extends TransferHandler {
    private final CodeTraceController controller;
    private final Runnable refreshUi;

    public NodeListReorderTransferHandler(CodeTraceController controller, Runnable refreshUi) {
        this.controller = controller;
        this.refreshUi = refreshUi;
    }

    @Override
    public boolean canImport(TransferSupport support) {
        return support.isDrop() && support.getComponent() instanceof JBList<?>;
    }

    @Override
    public boolean importData(TransferSupport support) {
        JList.DropLocation dropLocation = (JList.DropLocation) support.getDropLocation();
        JBList<TraceNode> list = (JBList<TraceNode>) support.getComponent();
        TraceNode selected = list.getSelectedValue();
        if (selected == null) {
            return false;
        }
        controller.moveNodeOrPairToIndex(selected.id(), dropLocation.getIndex());
        refreshUi.run();
        return true;
    }
}
