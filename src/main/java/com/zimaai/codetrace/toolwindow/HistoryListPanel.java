package com.zimaai.codetrace.toolwindow;

import com.intellij.ui.components.JBList;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

public final class HistoryListPanel {
    private final JBList<String> list = new JBList<>();
    private final JPanel root = new JPanel(new BorderLayout());

    public HistoryListPanel() {
        root.add(list, BorderLayout.CENTER);
    }

    public JComponent component() {
        return root;
    }

    public JBList<String> list() {
        return list;
    }
}
