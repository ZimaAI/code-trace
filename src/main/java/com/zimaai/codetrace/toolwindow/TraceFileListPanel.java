package com.zimaai.codetrace.toolwindow;

import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;
import java.awt.BorderLayout;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.JPanel;

public final class TraceFileListPanel {
    private final JBList<String> list = new JBList<>();
    private final JPanel root = new JPanel(new BorderLayout());

    public TraceFileListPanel() {
        root.add(ToolbarDecorator.createDecorator(list).createPanel(), BorderLayout.CENTER);
    }

    public void configureActions(
            Runnable createAction,
            Runnable renameAction,
            Runnable copyAction,
            Runnable deleteAction,
            Runnable refreshAction) {
        Objects.requireNonNull(createAction, "createAction");
        Objects.requireNonNull(renameAction, "renameAction");
        Objects.requireNonNull(copyAction, "copyAction");
        Objects.requireNonNull(deleteAction, "deleteAction");
        Objects.requireNonNull(refreshAction, "refreshAction");
        root.removeAll();
        root.add(ToolbarDecorator.createDecorator(list)
                .setAddAction(actionButton -> createAction.run())
                .setEditAction(actionButton -> renameAction.run())
                .setRemoveAction(actionButton -> deleteAction.run())
                .addExtraAction(new com.intellij.ui.AnActionButton("Copy") {
                    @Override
                    public void actionPerformed(com.intellij.openapi.actionSystem.AnActionEvent anActionEvent) {
                        copyAction.run();
                    }
                })
                .addExtraAction(new com.intellij.ui.AnActionButton("Refresh") {
                    @Override
                    public void actionPerformed(com.intellij.openapi.actionSystem.AnActionEvent anActionEvent) {
                        refreshAction.run();
                    }
                })
                .createPanel(), BorderLayout.CENTER);
        root.revalidate();
        root.repaint();
    }

    public JComponent component() {
        return root;
    }

    public JBList<String> list() {
        return list;
    }
}
