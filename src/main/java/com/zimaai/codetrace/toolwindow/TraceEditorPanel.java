package com.zimaai.codetrace.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import javax.swing.JTable;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.Color;
import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;

public final class TraceEditorPanel {
    private final JBTextArea traceNote = new JBTextArea();
    private final JButton saveTraceNoteButton = new JButton("Save Trace Note", AllIcons.Actions.MenuSaveall);
    private final JTable nodeTable = new JTable();
    private final JBTextArea nodeNote = new JBTextArea();
    private final JButton saveNodeNoteButton = new JButton("Save Node Note", AllIcons.Actions.MenuSaveall);
    private final JButton editNodeButton = new JButton("Edit Node", AllIcons.Actions.Edit);
    private final JButton deleteNodeButton = new JButton("Delete Node", AllIcons.General.Remove);
    private final JButton moveUpButton = new JButton("Move Up", AllIcons.General.ArrowUp);
    private final JButton moveDownButton = new JButton("Move Down", AllIcons.General.ArrowDown);
    private final JButton setAsSourceButton = new JButton("Set as Source", AllIcons.Actions.PinTab);
    private final JButton linkToHereButton = new JButton("Link To Here", AllIcons.General.LinkDropTriangle);
    private final JButton unlinkButton = new JButton("Unlink", AllIcons.Actions.DeleteTag);
    private final JButton goToLinkedButton = new JButton("Go To Linked", AllIcons.Actions.Find);
    private final JButton expandAllButton = new JButton("Expand All", AllIcons.Actions.Expandall);
    private final JButton collapseAllButton = new JButton("Collapse All", AllIcons.Actions.Collapseall);
    private final JLabel linkStatus = new JLabel("Link source: none");
    private final JPanel nodeToolbar = new JPanel(new WrapLayout(WrapLayout.LEFT, 4, 4));
    private final JPanel root = new JPanel(new BorderLayout());
    private FilteredNodeTableModel filteredModel;
    private CollapseIndicatorRenderer collapseRenderer;
    private java.awt.event.MouseListener collapseMouseListener;
    private final java.util.List<CollapseExpandListener> collapseExpandListeners = new java.util.ArrayList<>();

    public TraceEditorPanel() {
        configureTextArea(traceNote);
        configureTextArea(nodeNote);

        // Configure JTable
        nodeTable.setShowGrid(false);
        nodeTable.setIntercellSpacing(new java.awt.Dimension(0, 0));
        nodeTable.setRowHeight(24);
        nodeTable.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        nodeTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        JPanel traceNotePanel = new JPanel(new BorderLayout());
        traceNotePanel.setBorder(JBUI.Borders.empty(0, 0, 6, 0));
        traceNotePanel.add(new JBScrollPane(traceNote), BorderLayout.CENTER);
        traceNotePanel.add(saveTraceNoteButton, BorderLayout.SOUTH);

        goToLinkedButton.setEnabled(false);

        configureNodeToolbar();

        JPanel nodeNotePanel = new JPanel(new BorderLayout());
        nodeNotePanel.add(new JBScrollPane(nodeNote), BorderLayout.CENTER);
        nodeNotePanel.add(saveNodeNoteButton, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JBScrollPane(nodeTable),
                nodeNotePanel);
        split.setResizeWeight(0.7d);

        linkStatus.setBorder(JBUI.Borders.empty(3, 6, 2, 6));

        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(JBUI.Borders.empty(4, 0, 0, 0));
        content.add(nodeToolbar, BorderLayout.NORTH);
        content.add(split, BorderLayout.CENTER);
        content.add(linkStatus, BorderLayout.SOUTH);

        root.add(traceNotePanel, BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);

        addTooltips();
    }

    private void configureNodeToolbar() {
        // Row 1: Node editing actions
        nodeToolbar.add(editNodeButton);
        nodeToolbar.add(deleteNodeButton);
        nodeToolbar.add(new JSeparator(SwingConstants.VERTICAL));
        // Row 1 continued: Ordering
        nodeToolbar.add(moveUpButton);
        nodeToolbar.add(moveDownButton);
        nodeToolbar.add(new JSeparator(SwingConstants.VERTICAL));
        // Row 1 continued: Link source selection
        nodeToolbar.add(setAsSourceButton);
        nodeToolbar.add(linkToHereButton);
        nodeToolbar.add(unlinkButton);
        nodeToolbar.add(new JSeparator(SwingConstants.VERTICAL));
        // Row 1 continued: Navigation
        nodeToolbar.add(goToLinkedButton);
        nodeToolbar.add(new JSeparator(SwingConstants.VERTICAL));
        // Row 1 continued: Expand/Collapse All
        nodeToolbar.add(expandAllButton);
        nodeToolbar.add(collapseAllButton);
    }

    private void addTooltips() {
        editNodeButton.setToolTipText("Edit the selected node's properties");
        deleteNodeButton.setToolTipText("Remove the selected node (and its link pair, if linked)");
        moveUpButton.setToolTipText("Move the selected node up in the list");
        moveDownButton.setToolTipText("Move the selected node down in the list");
        setAsSourceButton.setToolTipText("Mark the selected node as the link source for a future link");
        linkToHereButton.setToolTipText("Create a trace link from the pending source node to the selected node");
        unlinkButton.setToolTipText("Remove the trace link associated with the selected node");
        goToLinkedButton.setToolTipText("Navigate to linked nodes. Click to jump if only one link exists, or show a menu for multiple links.");
        saveTraceNoteButton.setToolTipText("Save the trace-level description");
        saveNodeNoteButton.setToolTipText("Save the note for the selected node");
        expandAllButton.setToolTipText("Expand all parent nodes");
        collapseAllButton.setToolTipText("Collapse all parent nodes");
    }

    private static void configureTextArea(JBTextArea area) {
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setSelectionColor(new JBColor(
                () -> resolveEditorColor(EditorColors.SELECTION_BACKGROUND_COLOR, UIUtil.getListSelectionBackground(), 0.85)));
        area.setSelectedTextColor(new JBColor(
                () -> resolveEditorColor(EditorColors.SELECTION_FOREGROUND_COLOR, UIUtil.getListSelectionForeground(), 1.0)));
        area.setCaretColor(new JBColor(
                () -> resolveEditorColor(EditorColors.CARET_COLOR, UIUtil.getTextAreaForeground(), 1.0)));
    }

    private static Color resolveEditorColor(com.intellij.openapi.editor.colors.ColorKey key, Color fallback, double alpha) {
        Color color = null;
        if (ApplicationManager.getApplication() != null) {
            color = EditorColorsManager.getInstance().getGlobalScheme().getColor(key);
        }
        Color base = color == null ? fallback : color;
        return alpha >= 1.0 ? base : ColorUtil.withAlpha(base, alpha);
    }

    public JComponent component() {
        return root;
    }

    public JBTextArea traceNote() {
        return traceNote;
    }

    public JButton saveTraceNoteButton() {
        return saveTraceNoteButton;
    }

    public JTable nodeTable() {
        return nodeTable;
    }

    public JBTextArea nodeNote() {
        return nodeNote;
    }

    public JButton saveNodeNoteButton() {
        return saveNodeNoteButton;
    }

    public JButton editNodeButton() {
        return editNodeButton;
    }

    public JButton deleteNodeButton() {
        return deleteNodeButton;
    }

    public JButton moveUpButton() {
        return moveUpButton;
    }

    public JButton moveDownButton() {
        return moveDownButton;
    }

    public JButton setAsSourceButton() {
        return setAsSourceButton;
    }

    public JButton linkToHereButton() {
        return linkToHereButton;
    }

    public JButton unlinkButton() {
        return unlinkButton;
    }

    public JLabel linkStatus() {
        return linkStatus;
    }

    JPanel nodeToolbar() {
        return nodeToolbar;
    }

    JButton goToLinkedButton() {
        return goToLinkedButton;
    }

    public JButton expandAllButton() {
        return expandAllButton;
    }

    public JButton collapseAllButton() {
        return collapseAllButton;
    }

    /**
     * 配置表格支持折叠功能
     */
    public void configureTableWithCollapseSupport(NodeTableModel sourceModel, TraceDocument document) {
        // 总是创建新的过滤模型，确保数据一致性
        // 列宽保持通过 CodeTracePanel.restoreColumnWidths() 实现
        filteredModel = new FilteredNodeTableModel(sourceModel, document);

        // 设置表格模型
        nodeTable.setModel(filteredModel);

        // 创建并设置渲染器
        collapseRenderer = new CollapseIndicatorRenderer(filteredModel);
        nodeTable.getColumnModel().getColumn(0).setCellRenderer(collapseRenderer);

        // 移除旧的监听器
        if (collapseMouseListener != null) {
            nodeTable.removeMouseListener(collapseMouseListener);
        }

        // 添加新的点击监听器处理折叠/展开
        collapseMouseListener = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = nodeTable.rowAtPoint(e.getPoint());
                int col = nodeTable.columnAtPoint(e.getPoint());

                if (col == 0 && row >= 0 && row < filteredModel.getRowCount()) {
                    TraceNode node = filteredModel.getNodeAt(row);
                    boolean hasChildren = filteredModel.getSourceModel().hasChildren(node.id());

                    if (hasChildren) {
                        // 触发折叠/展开事件
                        fireCollapseExpandEvent(node.id());
                    }
                }
            }
        };
        nodeTable.addMouseListener(collapseMouseListener);
    }

    /**
     * 更新文档状态
     */
    public void updateDocument(TraceDocument document) {
        if (filteredModel != null) {
            filteredModel.setDocument(document);
            // 渲染器会通过 filteredModel.getDocument() 获取最新状态
        }
    }

    /**
     * 获取过滤模型
     */
    public FilteredNodeTableModel getFilteredModel() {
        return filteredModel;
    }

    public interface CollapseExpandListener {
        void onCollapseExpand(String nodeId);
    }

    public void addCollapseExpandListener(CollapseExpandListener listener) {
        collapseExpandListeners.add(listener);
    }

    private void fireCollapseExpandEvent(String nodeId) {
        for (CollapseExpandListener listener : collapseExpandListeners) {
            listener.onCollapseExpand(nodeId);
        }
    }
}
