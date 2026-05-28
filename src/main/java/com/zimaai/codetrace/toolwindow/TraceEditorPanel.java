package com.zimaai.codetrace.toolwindow;

import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

public final class TraceEditorPanel {
    private final JBTextArea traceNote = new JBTextArea();
    private final JBList<String> nodeList = new JBList<>();
    private final JBTextArea nodeNote = new JBTextArea();
    private final JPanel root = new JPanel(new BorderLayout());
    private final JButton addNodeButton = new JButton("Add Node");
    private final JButton editNodeButton = new JButton("Edit Node");
    private final JButton deleteNodeButton = new JButton("Delete Node");
    private final JButton moveUpButton = new JButton("Move Up");
    private final JButton moveDownButton = new JButton("Move Down");

    public TraceEditorPanel() {
        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JBScrollPane(nodeList),
                new JBScrollPane(nodeNote));
        split.setResizeWeight(0.7d);
        JPanel nodeToolbar = new JPanel();
        nodeToolbar.add(addNodeButton);
        nodeToolbar.add(editNodeButton);
        nodeToolbar.add(deleteNodeButton);
        nodeToolbar.add(moveUpButton);
        nodeToolbar.add(moveDownButton);
        JPanel content = new JPanel(new BorderLayout());
        content.add(nodeToolbar, BorderLayout.NORTH);
        content.add(split, BorderLayout.CENTER);
        root.add(new JBScrollPane(traceNote), BorderLayout.NORTH);
        root.add(content, BorderLayout.CENTER);
    }

    public JComponent component() {
        return root;
    }

    public JBTextArea traceNote() {
        return traceNote;
    }

    public JBList<String> nodeList() {
        return nodeList;
    }

    public JBTextArea nodeNote() {
        return nodeNote;
    }

    public JButton addNodeButton() {
        return addNodeButton;
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
}
