package com.zimaai.codetrace.toolwindow;

import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.UIUtil;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.Color;
import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

public final class TraceEditorPanel {
    private final JBTextArea traceNote = new JBTextArea();
    private final JButton saveTraceNoteButton = new JButton("Save Trace Note");
    private final JBList<TraceNode> nodeList = new JBList<>();
    private final JBTextArea nodeNote = new JBTextArea();
    private final JButton saveNodeNoteButton = new JButton("Save Node Note");
    private final JButton editNodeButton = new JButton("Edit Node");
    private final JButton deleteNodeButton = new JButton("Delete Node");
    private final JButton moveUpButton = new JButton("Move Up");
    private final JButton moveDownButton = new JButton("Move Down");
    private final JButton setAsSourceButton = new JButton("Set as Source");
    private final JButton linkToHereButton = new JButton("Link To Here");
    private final JButton unlinkButton = new JButton("Unlink");
    private final JLabel linkStatus = new JLabel("Link source: none");
    private final JPanel root = new JPanel(new BorderLayout());

    public TraceEditorPanel() {
        configureTextArea(traceNote);
        configureTextArea(nodeNote);

        JPanel traceNotePanel = new JPanel(new BorderLayout());
        traceNotePanel.add(new JBScrollPane(traceNote), BorderLayout.CENTER);
        traceNotePanel.add(saveTraceNoteButton, BorderLayout.SOUTH);

        JPanel nodeToolbar = new JPanel();
        nodeToolbar.add(editNodeButton);
        nodeToolbar.add(deleteNodeButton);
        nodeToolbar.add(moveUpButton);
        nodeToolbar.add(moveDownButton);
        nodeToolbar.add(setAsSourceButton);
        nodeToolbar.add(linkToHereButton);
        nodeToolbar.add(unlinkButton);

        JPanel nodeNotePanel = new JPanel(new BorderLayout());
        nodeNotePanel.add(new JBScrollPane(nodeNote), BorderLayout.CENTER);
        nodeNotePanel.add(saveNodeNoteButton, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JBScrollPane(nodeList),
                nodeNotePanel);
        split.setResizeWeight(0.7d);

        JPanel content = new JPanel(new BorderLayout());
        content.add(nodeToolbar, BorderLayout.NORTH);
        content.add(split, BorderLayout.CENTER);
        content.add(linkStatus, BorderLayout.SOUTH);

        root.add(traceNotePanel, BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
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

    public JBList<TraceNode> nodeList() {
        return nodeList;
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
}
