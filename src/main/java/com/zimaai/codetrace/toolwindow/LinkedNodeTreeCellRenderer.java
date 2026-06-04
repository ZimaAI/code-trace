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
import javax.swing.JLabel;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;

public final class LinkedNodeTreeCellRenderer extends DefaultTreeCellRenderer {
    static final Color PENDING_SOURCE_COLOR = new JBColor(new Color(255, 243, 205), new Color(77, 59, 24));
    static final Color SOURCE_COLOR = new JBColor(new Color(219, 234, 254), new Color(28, 55, 80));
    static final Color TARGET_COLOR = new JBColor(new Color(220, 252, 231), new Color(24, 64, 35));

    static final Color PENDING_SOURCE_BORDER = new JBColor(new Color(234, 179, 8), new Color(202, 138, 4));
    static final Color SOURCE_BORDER = new JBColor(new Color(96, 165, 250), new Color(59, 130, 246));
    static final Color TARGET_BORDER = new JBColor(new Color(74, 222, 128), new Color(34, 197, 94));

    static final String FOCUS_PREFIX = "● ";
    static final String ROLE_PENDING_SOURCE = "◉ ";
    static final String ROLE_SOURCE = "▶ ";
    static final String ROLE_TARGET = "◀ ";

    private static final int MAX_TITLE_CHARS = 15;
    private static final Color TITLE_COLOR = JBColor.GRAY;

    private final Supplier<TraceDocument> documentSupplier;
    private final Supplier<String> focusedNodeIdSupplier;
    private final Supplier<String> pendingSourceSupplier;

    public LinkedNodeTreeCellRenderer(
            Supplier<TraceDocument> documentSupplier,
            Supplier<String> focusedNodeIdSupplier,
            Supplier<String> pendingSourceSupplier) {
        this.documentSupplier = Objects.requireNonNull(documentSupplier, "documentSupplier");
        this.focusedNodeIdSupplier = Objects.requireNonNull(focusedNodeIdSupplier, "focusedNodeIdSupplier");
        this.pendingSourceSupplier = Objects.requireNonNull(pendingSourceSupplier, "pendingSourceSupplier");
    }

    @Override
    public Component getTreeCellRendererComponent(
            JTree tree, Object value, boolean selected, boolean expanded,
            boolean leaf, int row, boolean hasFocus) {
        JLabel label = (JLabel) super.getTreeCellRendererComponent(
                tree, "", selected, expanded, leaf, row, hasFocus);

        if (!(value instanceof TraceNode node) || node == TraceTreeModel.VIRTUAL_ROOT) {
            return label;
        }

        label.setOpaque(true);
        label.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));

        StringBuilder prefix = new StringBuilder();

        // Focus indicator
        if (node.id().equals(focusedNodeIdSupplier.get())) {
            prefix.append(FOCUS_PREFIX);
        }

        // Role indicator
        String pendingSource = pendingSourceSupplier.get();
        TraceDocument document = documentSupplier.get();

        if (!selected) {
            if (node.id().equals(pendingSource)) {
                prefix.append(ROLE_PENDING_SOURCE);
                label.setBackground(PENDING_SOURCE_COLOR);
                label.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 3, 0, 0, PENDING_SOURCE_BORDER),
                        BorderFactory.createEmptyBorder(3, 6, 3, 4)));
            } else if (document != null) {
                boolean roleApplied = false;
                for (TraceLink link : document.links()) {
                    if (!TraceDocumentEditor.isLinkValid(link, document)) {
                        continue;
                    }
                    if (node.id().equals(link.sourceNodeId())) {
                        prefix.append(ROLE_SOURCE);
                        label.setBackground(SOURCE_COLOR);
                        label.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createMatteBorder(0, 3, 0, 0, SOURCE_BORDER),
                                BorderFactory.createEmptyBorder(3, 6, 3, 4)));
                        roleApplied = true;
                        break;
                    }
                    if (node.id().equals(link.targetNodeId())) {
                        prefix.append(ROLE_TARGET);
                        label.setBackground(TARGET_COLOR);
                        label.setBorder(BorderFactory.createCompoundBorder(
                                BorderFactory.createMatteBorder(0, 3, 0, 0, TARGET_BORDER),
                                BorderFactory.createEmptyBorder(3, 6, 3, 4)));
                        roleApplied = true;
                        break;
                    }
                }
                if (!roleApplied) {
                    label.setBackground(null);
                }
            }
        }

        // Title + displayName
        String title = node.title();
        if (title != null && !title.isBlank()) {
            String truncated = title.length() > MAX_TITLE_CHARS
                    ? title.substring(0, MAX_TITLE_CHARS) + "…" : title;
            label.setText(prefix + truncated + " — " + node.displayName());
        } else {
            label.setText(prefix + node.displayName());
        }

        return label;
    }
}
