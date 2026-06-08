package com.zimaai.codetrace.toolwindow;

import com.intellij.icons.AllIcons;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.FlowLayout;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.swing.JButton;
import javax.swing.JPanel;

final class NodeRowActionsPanel extends JPanel {
    private final Map<NodeRowAction, JButton> buttons = new EnumMap<>(NodeRowAction.class);
    private TraceNode node;

    NodeRowActionsPanel(boolean interactive, BiConsumer<NodeRowAction, TraceNode> actionHandler) {
        super(new FlowLayout(FlowLayout.CENTER, 2, 0));
        setOpaque(true);
        addButton(NodeRowAction.EDIT, new JButton(AllIcons.Actions.Edit), "Edit node", interactive, actionHandler);
        addButton(NodeRowAction.DELETE, new JButton(AllIcons.General.Remove), "Delete node", interactive, actionHandler);
        addButton(NodeRowAction.MOVE_UP, new JButton(AllIcons.General.ArrowUp), "Move node up", interactive, actionHandler);
        addButton(
                NodeRowAction.MOVE_DOWN,
                new JButton(AllIcons.General.ArrowDown),
                "Move node down",
                interactive,
                actionHandler);
    }

    void setNode(TraceNode node) {
        this.node = node;
    }

    JButton button(NodeRowAction action) {
        return buttons.get(action);
    }

    private void addButton(
            NodeRowAction action,
            JButton button,
            String tooltip,
            boolean interactive,
            BiConsumer<NodeRowAction, TraceNode> actionHandler) {
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setMargin(com.intellij.util.ui.JBUI.insets(1));
        button.setEnabled(interactive);
        if (interactive) {
            button.addActionListener(event -> {
                if (node != null) {
                    actionHandler.accept(action, node);
                }
            });
        }
        buttons.put(action, button);
        add(button);
    }
}
