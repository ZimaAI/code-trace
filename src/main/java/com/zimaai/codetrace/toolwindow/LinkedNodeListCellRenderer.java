package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceNode;
import com.intellij.ui.JBColor;
import java.awt.Color;
import java.awt.Component;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;

public final class LinkedNodeListCellRenderer extends DefaultListCellRenderer {
    static final Color PENDING_SOURCE_COLOR = new JBColor(new Color(255, 244, 214), new Color(78, 61, 25));
    static final Color SOURCE_COLOR = new JBColor(new Color(222, 242, 255), new Color(30, 58, 82));
    static final Color TARGET_COLOR = new JBColor(new Color(228, 255, 230), new Color(30, 73, 41));

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
                label.setBackground(PENDING_SOURCE_COLOR);
            } else if (document != null) {
                for (TraceLink link : document.links()) {
                    if (node.id().equals(link.sourceNodeId())) {
                        label.setBackground(SOURCE_COLOR);
                        break;
                    }
                    if (node.id().equals(link.targetNodeId())) {
                        label.setBackground(TARGET_COLOR);
                        break;
                    }
                }
            }
        }
        return label;
    }
}
