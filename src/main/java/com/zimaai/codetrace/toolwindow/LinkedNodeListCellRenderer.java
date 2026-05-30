package com.zimaai.codetrace.toolwindow;

import com.intellij.ui.JBColor;
import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.Color;
import java.awt.Component;
import java.util.Objects;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;

public final class LinkedNodeListCellRenderer extends DefaultListCellRenderer {
    static final Color PENDING_SOURCE_COLOR = new JBColor(new Color(255, 243, 205), new Color(77, 59, 24));
    static final Color SOURCE_COLOR = new JBColor(new Color(219, 234, 254), new Color(28, 55, 80));
    static final Color TARGET_COLOR = new JBColor(new Color(220, 252, 231), new Color(24, 64, 35));

    static final Color PENDING_SOURCE_BORDER = new JBColor(new Color(234, 179, 8), new Color(202, 138, 4));
    static final Color SOURCE_BORDER = new JBColor(new Color(96, 165, 250), new Color(59, 130, 246));
    static final Color TARGET_BORDER = new JBColor(new Color(74, 222, 128), new Color(34, 197, 94));

    private static final String ROLE_PENDING_SOURCE = "◉ "; // ◉
    private static final String ROLE_SOURCE = "▶ "; // ▶
    private static final String ROLE_TARGET = "◀ "; // ◀

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
        label.setOpaque(true);
        label.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 4));

        TraceDocument document = documentSupplier.get();
        String pendingSource = pendingSourceSupplier.get();

        if (!isSelected) {
            if (node.id().equals(pendingSource)) {
                label.setText(ROLE_PENDING_SOURCE + node.displayName());
                label.setBackground(PENDING_SOURCE_COLOR);
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, PENDING_SOURCE_BORDER),
                        BorderFactory.createEmptyBorder(3, 6, 3, 4)));
            } else if (document != null) {
                for (TraceLink link : document.links()) {
                    if (node.id().equals(link.sourceNodeId())) {
                        label.setText(ROLE_SOURCE + node.displayName());
                        label.setBackground(SOURCE_COLOR);
                        label.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createMatteBorder(0, 3, 0, 0, SOURCE_BORDER),
                                BorderFactory.createEmptyBorder(3, 6, 3, 4)));
                        return label;
                    }
                    if (node.id().equals(link.targetNodeId())) {
                        label.setText(ROLE_TARGET + node.displayName());
                        label.setBackground(TARGET_COLOR);
                        label.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createMatteBorder(0, 3, 0, 0, TARGET_BORDER),
                                BorderFactory.createEmptyBorder(3, 6, 3, 4)));
                        return label;
                    }
                }
            }
        }
        if (isSelected || label.getText().isEmpty()) {
            label.setText(node.displayName());
        }
        return label;
    }
}
