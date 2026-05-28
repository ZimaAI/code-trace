package com.zimaai.codetrace.toolwindow;

import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.components.JBTextArea;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

public final class TraceEditorPanel {
    private final JBTextArea traceNote = new JBTextArea();
    private final JBList<String> nodeList = new JBList<>();
    private final JBTextArea nodeNote = new JBTextArea();
    private final JPanel root = new JPanel(new BorderLayout());

    public TraceEditorPanel() {
        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JBScrollPane(nodeList),
                new JBScrollPane(nodeNote));
        split.setResizeWeight(0.7d);
        root.add(new JBScrollPane(traceNote), BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
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
}
