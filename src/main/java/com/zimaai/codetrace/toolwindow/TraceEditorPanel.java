package com.zimaai.codetrace.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import javax.swing.JTree;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
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
    private final JTree nodeTree = new JTree();
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
    private final JLabel linkStatus = new JLabel("Link source: none");
    private final JPanel nodeToolbar = new JPanel(new WrapLayout(WrapLayout.LEFT, 4, 4));
    private final JPanel root = new JPanel(new BorderLayout());

    public TraceEditorPanel() {
        configureTextArea(traceNote);
        configureTextArea(nodeNote);

        // Configure JTree
        nodeTree.setRootVisible(false);
        nodeTree.setShowsRootHandles(true);
        nodeTree.setEditable(false);
        nodeTree.setToggleClickCount(0);

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
                new JBScrollPane(nodeTree),
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

    public JTree nodeTree() {
        return nodeTree;
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
}
