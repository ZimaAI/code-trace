package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.Color;
import java.awt.Component;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;

public final class LinkedNodeListCellRenderer extends DefaultListCellRenderer {
    private final Supplier<TraceDocument> documentSupplier;
    private final Supplier<String> pendingSourceSupplier;

    public LinkedNodeListCellRenderer(Supplier<TraceDocument> documentSupplier, Supplier<String> pendingSourceSupplier) {
        this.documentSupplier = Objects.requireNonNull(documentSupplier, "documentSupplier");
        this.pendingSourceSupplier = Objects.requireNonNull(pendingSourceSupplier, "pendingSourceSupplier");
    }

    @Override
    public Component getListCellRendererComponent(
            JList<?> list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {
        JLabel label = (JLabel) super.getListCellRendererComponent(list, "", index, isSelected, cellHasFocus);
        if (!(value instanceof TraceNode node)) {
            return label;
        }
        label.setText(node.displayName());
        label.setOpaque(true);

        TraceDocument document = documentSupplier.get();
        String pendingSource = pendingSourceSupplier.get();

        if (!isSelected) {
            if (node.id().equals(pendingSource)) {
                label.setBackground(new Color(255, 244, 214));
            } else if (document != null) {
                for (TraceLink link : document.links()) {
                    if (node.id().equals(link.sourceNodeId())) {
                        label.setBackground(new Color(222, 242, 255));
                        break;
                    }
                    if (node.id().equals(link.targetNodeId())) {
                        label.setBackground(new Color(228, 255, 230));
                        break;
                    }
                }
            }
        }
        return label;
    }
}
