package com.zimaai.codetrace.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.ui.JBSplitter;
import com.intellij.util.ui.JBUI;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.BorderLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class CodeTracePanel {
    private static final List<String> TOP_TOOLBAR_BUTTON_LABELS = List.of("Refresh");
    private final CodeTraceController controller;
    private final JPanel root = new JPanel(new BorderLayout());
    private final Map<String, JButton> buttons = new HashMap<>();
    private final TraceFileListPanel fileListPanel = new TraceFileListPanel();
    private final TraceEditorPanel editorPanel = new TraceEditorPanel();
    private boolean syncingTraceNote;
    private boolean syncingNodeNote;
    private boolean syncingNodeSelection;
    private String persistedTraceNote = "";
    private String persistedNodeNote = "";
    private String selectedNodeId;

    public CodeTracePanel(CodeTraceController controller) {
        this.controller = controller;
        editorPanel.nodeList().setCellRenderer(
                new LinkedNodeListCellRenderer(() -> controller.state().currentDocument(), () -> controller.state().pendingLinkSourceId()));
        configureLeftPaneActions();
        configureLayout();
        wireSelection();
        wireNoteButtons();
        wireNodeActions();
    }

    public JComponent getComponent() {
        return root;
    }

    public JButton findButton(String text) {
        return buttons.get(text);
    }

    public void reloadFromDisk() {
        controller.ensureAnyFileLoaded();
        rebuildView();
    }

    public void refreshFromExternalAction() {
        controller.refreshCurrentFile();
        rebuildView();
    }

    private void configureLeftPaneActions() {
        fileListPanel.configureActions(
                this::createFile,
                this::renameSelectedFile,
                this::copySelectedFile,
                this::deleteSelectedFile,
                this::refreshAndRepaint);
    }

    private void configureLayout() {
        JPanel toolbar = new JPanel();
        toolbar.setBorder(JBUI.Borders.empty(4, 4, 4, 4));
        for (String label : TOP_TOOLBAR_BUTTON_LABELS) {
            addButton(toolbar, label, topToolbarAction(label));
        }

        JBSplitter split = new JBSplitter(false, 0.25f);
        // Allow users to freely resize file list vs editor list panes.
        split.setHonorComponentsMinimumSize(false);
        split.setDividerWidth(12);
        split.setShowDividerControls(true);
        split.setAllowSwitchOrientationByMouseClick(false);
        split.setFirstComponent(fileListPanel.component());
        split.setSecondComponent(editorPanel.component());

        root.add(toolbar, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
    }

    private Runnable topToolbarAction(String label) {
        return switch (label) {
            case "Refresh" -> this::refreshAndRepaint;
            default -> throw new IllegalArgumentException("Unknown top toolbar button: " + label);
        };
    }

    private void wireSelection() {
        fileListPanel.list().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            String selected = fileListPanel.list().getSelectedValue();
            if (selected == null || selected.equals(controller.state().currentFileName())) {
                return;
            }
            controller.load(selected);
            rebuildView();
        });

        editorPanel.nodeList().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting() || syncingNodeSelection) {
                return;
            }
            TraceNode selected = editorPanel.nodeList().getSelectedValue();
            selectedNodeId = selected == null ? null : selected.id();
            syncSelectedNodeNote();
            refreshButtons();
        });

        editorPanel.nodeList().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2 && editorPanel.nodeList().getSelectedValue() != null) {
                    controller.navigateToNode(editorPanel.nodeList().getSelectedValue());
                }
            }
        });
    }

    private void wireNoteButtons() {
        editorPanel.saveTraceNoteButton().addActionListener(event -> saveTraceNote());
        editorPanel.saveNodeNoteButton().addActionListener(event -> saveNodeNote());

        editorPanel.traceNote().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshButtons();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshButtons();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshButtons();
            }
        });
        editorPanel.nodeNote().getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshButtons();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshButtons();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshButtons();
            }
        });
    }

    private void wireNodeActions() {
        editorPanel.editNodeButton().addActionListener(event -> editSelectedNode());
        editorPanel.deleteNodeButton().addActionListener(event -> deleteSelectedNode());
        editorPanel.moveUpButton().addActionListener(event -> moveSelectedNode(-1));
        editorPanel.moveDownButton().addActionListener(event -> moveSelectedNode(1));
        editorPanel.setAsSourceButton().addActionListener(event -> setSelectedAsSource());
        editorPanel.linkToHereButton().addActionListener(event -> linkToSelectedNode());
        editorPanel.unlinkButton().addActionListener(event -> unlinkSelectedNode());
        editorPanel.goToLinkedButton().addActionListener(event -> goToLinked());
    }

    private void addButton(JPanel toolbar, String label, Runnable action) {
        JButton button = new JButton(label, AllIcons.Actions.Refresh);
        button.addActionListener(event -> action.run());
        button.setToolTipText("Reload trace data from disk");
        buttons.put(label, button);
        toolbar.add(button);
    }

    private void createFile() {
        String fileName = JOptionPane.showInputDialog(root, "New JSON file name", "new-trace.json");
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        String normalized = fileName.endsWith(".json") ? fileName : fileName + ".json";
        controller.createNewFile(normalized, normalized.replace(".json", ""));
        rebuildView();
        fileListPanel.list().setSelectedValue(normalized, true);
    }

    private void renameSelectedFile() {
        String current = controller.state().currentFileName();
        if (current == null) {
            return;
        }
        String newName = JOptionPane.showInputDialog(root, "Rename file", current);
        if (newName == null || newName.isBlank()) {
            return;
        }
        String normalized = newName.endsWith(".json") ? newName : newName + ".json";
        controller.renameCurrentFile(normalized);
        rebuildView();
        fileListPanel.list().setSelectedValue(normalized, true);
    }

    private void copySelectedFile() {
        String current = controller.state().currentFileName();
        if (current == null) {
            return;
        }
        String newName = JOptionPane.showInputDialog(root, "Copy as", current.replace(".json", "-copy.json"));
        if (newName == null || newName.isBlank()) {
            return;
        }
        String normalized = newName.endsWith(".json") ? newName : newName + ".json";
        controller.copyCurrentFile(normalized);
        rebuildView();
    }

    private void deleteSelectedFile() {
        String current = controller.state().currentFileName();
        if (current == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(
                root,
                "Delete " + current + "?",
                "Confirm Delete",
                JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) {
            return;
        }
        controller.deleteCurrentFile();
        controller.ensureAnyFileLoaded();
        rebuildView();
    }

    private void refreshAndRepaint() {
        controller.refreshCurrentFile();
        rebuildView();
    }

    private void saveTraceNote() {
        if (controller.state().currentDocument() == null) {
            return;
        }
        controller.saveDescription(editorPanel.traceNote().getText());
        rebuildView();
    }

    private void saveNodeNote() {
        if (selectedNodeId == null) {
            return;
        }
        controller.saveNodeNote(selectedNodeId, editorPanel.nodeNote().getText());
        rebuildView();
    }

    private void setSelectedAsSource() {
        if (selectedNodeId == null) {
            return;
        }
        controller.setPendingLinkSource(selectedNodeId);
        rebuildView();
    }

    private void linkToSelectedNode() {
        if (selectedNodeId == null || controller.state().pendingLinkSourceId() == null) {
            return;
        }
        try {
            controller.linkPendingSourceTo(selectedNodeId, TraceLinkKind.MANUAL);
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(root, exception.getMessage());
        }
        rebuildView();
    }

    private void unlinkSelectedNode() {
        if (selectedNodeId == null) {
            return;
        }
        controller.unlinkNode(selectedNodeId);
        rebuildView();
    }

    private void editSelectedNode() {
        TraceNode existing = editorPanel.nodeList().getSelectedValue();
        if (existing == null) {
            return;
        }
        NodeInput input = showNodeDialog("Edit Node", existing);
        if (input == null) {
            return;
        }
        TraceNode updated = new TraceNode(
                existing.id(),
                input.displayName(),
                input.qualifiedName(),
                input.signature(),
                input.filePath(),
                input.line(),
                input.language(),
                input.note(),
                input.navigationHint());
        controller.updateNode(updated);
        rebuildView();
    }

    private void deleteSelectedNode() {
        if (selectedNodeId == null) {
            return;
        }
        String deletingNodeId = selectedNodeId;
        selectedNodeId = null;
        controller.deleteNodeOrPair(deletingNodeId);
        rebuildView();
    }

    private void moveSelectedNode(int offset) {
        if (selectedNodeId == null) {
            return;
        }
        controller.moveNodeOrPair(selectedNodeId, offset);
        rebuildView();
    }

    private void goToLinked() {
        LinkedNodes linked = findAllLinkedNodes();
        int total = linked.sources().size() + linked.targets().size();

        if (total == 0) {
            return;
        }

        if (total == 1) {
            TraceNode single = !linked.sources().isEmpty()
                    ? linked.sources().get(0)
                    : linked.targets().get(0);
            selectAndNavigateToNode(single);
            return;
        }

        showLinkedNodesMenu(linked);
    }

    private void showLinkedNodesMenu(LinkedNodes linked) {
        JPopupMenu menu = new JPopupMenu();

        if (!linked.sources().isEmpty()) {
            JMenuItem sourceLabel = new JMenuItem("Sources (→)");
            sourceLabel.setEnabled(false);
            menu.add(sourceLabel);
            Font defaultFont = sourceLabel.getFont();
            if (defaultFont != null) {
                sourceLabel.setFont(defaultFont.deriveFont(Font.BOLD));
            }

            for (TraceNode node : linked.sources()) {
                JMenuItem item = new JMenuItem(node.displayName());
                item.setToolTipText(node.filePath() + ":" + node.line());
                item.addActionListener(e -> selectAndNavigateToNode(node));
                menu.add(item);
            }
        }

        if (!linked.sources().isEmpty() && !linked.targets().isEmpty()) {
            menu.add(new JSeparator());
        }

        if (!linked.targets().isEmpty()) {
            JMenuItem targetLabel = new JMenuItem("Targets (←)");
            targetLabel.setEnabled(false);
            menu.add(targetLabel);
            Font defaultFont = targetLabel.getFont();
            if (defaultFont != null) {
                targetLabel.setFont(defaultFont.deriveFont(Font.BOLD));
            }

            for (TraceNode node : linked.targets()) {
                JMenuItem item = new JMenuItem(node.displayName());
                item.setToolTipText(node.filePath() + ":" + node.line());
                item.addActionListener(e -> selectAndNavigateToNode(node));
                menu.add(item);
            }
        }

        JButton button = editorPanel.goToLinkedButton();
        menu.show(button, 0, button.getHeight());
    }

    private void selectAndNavigateToNode(TraceNode node) {
        controller.navigateToNode(node);
        editorPanel.nodeList().setSelectedValue(node, true);
    }

    private NodeInput showNodeDialog(String title, TraceNode initial) {
        javax.swing.JTextField nameField = new javax.swing.JTextField(initial == null ? "" : initial.displayName());
        javax.swing.JTextField qualifiedField = new javax.swing.JTextField(initial == null ? "" : initial.qualifiedName());
        javax.swing.JTextField signatureField = new javax.swing.JTextField(initial == null ? "" : initial.signature());
        javax.swing.JTextField fileField = new javax.swing.JTextField(initial == null ? "" : initial.filePath());
        javax.swing.JTextField lineField = new javax.swing.JTextField(initial == null ? "1" : Integer.toString(initial.line()));
        javax.swing.JTextField languageField = new javax.swing.JTextField(initial == null ? "UNKNOWN" : initial.language());
        javax.swing.JTextField hintField = new javax.swing.JTextField(initial == null ? "" : initial.navigationHint());
        javax.swing.JTextField noteField = new javax.swing.JTextField(initial == null ? "" : initial.note());

        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1));
        panel.add(new javax.swing.JLabel("Display Name"));
        panel.add(nameField);
        panel.add(new javax.swing.JLabel("Qualified Name"));
        panel.add(qualifiedField);
        panel.add(new javax.swing.JLabel("Signature"));
        panel.add(signatureField);
        panel.add(new javax.swing.JLabel("File Path"));
        panel.add(fileField);
        panel.add(new javax.swing.JLabel("Line"));
        panel.add(lineField);
        panel.add(new javax.swing.JLabel("Language"));
        panel.add(languageField);
        panel.add(new javax.swing.JLabel("Navigation Hint"));
        panel.add(hintField);
        panel.add(new javax.swing.JLabel("Node Note"));
        panel.add(noteField);

        int result = JOptionPane.showConfirmDialog(root, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) {
            return null;
        }
        int line;
        try {
            line = Integer.parseInt(lineField.getText().trim());
        } catch (NumberFormatException exception) {
            line = 1;
        }
        return new NodeInput(
                nameField.getText().trim(),
                qualifiedField.getText().trim(),
                signatureField.getText().trim(),
                fileField.getText().trim(),
                Math.max(1, line),
                languageField.getText().trim().isEmpty() ? "UNKNOWN" : languageField.getText().trim(),
                noteField.getText(),
                hintField.getText().trim());
    }

    private void rebuildView() {
        if (controller.state().currentFileName() == null) {
            controller.ensureAnyFileLoaded();
        }
        List<String> files = controller.loadFileNames();
        fileListPanel.list().setListData(files.toArray(String[]::new));

        var document = controller.state().currentDocument();
        if (document == null) {
            syncingTraceNote = true;
            editorPanel.traceNote().setText("");
            syncingTraceNote = false;
            persistedTraceNote = "";
            syncingNodeSelection = true;
            editorPanel.nodeList().setListData(new TraceNode[0]);
            syncingNodeSelection = false;
            selectedNodeId = null;
            controller.consumePreferredSelectedNodeId();
            syncingNodeNote = true;
            editorPanel.nodeNote().setText("");
            syncingNodeNote = false;
            persistedNodeNote = "";
            editorPanel.linkStatus().setText("Link source: none");
            refreshButtons();
            return;
        }

        if (controller.state().currentFileName() != null) {
            fileListPanel.list().setSelectedValue(controller.state().currentFileName(), true);
        }

        syncingTraceNote = true;
        persistedTraceNote = document.description() == null ? "" : document.description();
        editorPanel.traceNote().setText(persistedTraceNote);
        syncingTraceNote = false;

        syncingNodeSelection = true;
        editorPanel.nodeList().setListData(document.nodes().toArray(TraceNode[]::new));
        restoreSelection(document.nodes());
        syncingNodeSelection = false;
        editorPanel.linkStatus().setText("Link source: "
                + (controller.state().pendingLinkSourceId() == null ? "none" : controller.state().pendingLinkSourceId()));

        syncSelectedNodeNote();
        refreshButtons();
    }

    private void syncSelectedNodeNote() {
        TraceNode selected = findSelectedNode();
        syncingNodeNote = true;
        persistedNodeNote = selected == null || selected.note() == null ? "" : selected.note();
        editorPanel.nodeNote().setText(persistedNodeNote);
        syncingNodeNote = false;
    }

    private void refreshButtons() {
        var document = controller.state().currentDocument();
        boolean hasDocument = document != null;
        boolean hasSelection = findSelectedNode() != null;
        boolean hasPendingSource = controller.state().pendingLinkSourceId() != null;
        if (hasDocument && !syncingTraceNote) {
            editorPanel.saveTraceNoteButton().setEnabled(!persistedTraceNote.equals(editorPanel.traceNote().getText()));
        } else {
            editorPanel.saveTraceNoteButton().setEnabled(false);
        }

        if (hasSelection && !syncingNodeNote) {
            editorPanel.saveNodeNoteButton().setEnabled(!persistedNodeNote.equals(editorPanel.nodeNote().getText()));
        } else {
            editorPanel.saveNodeNoteButton().setEnabled(false);
        }

        editorPanel.editNodeButton().setEnabled(hasSelection);
        editorPanel.deleteNodeButton().setEnabled(hasSelection);
        editorPanel.moveUpButton().setEnabled(hasSelection);
        editorPanel.moveDownButton().setEnabled(hasSelection);
        editorPanel.setAsSourceButton().setEnabled(hasSelection);
        editorPanel.linkToHereButton().setEnabled(hasSelection && hasPendingSource);
        editorPanel.unlinkButton().setEnabled(hasSelection);
        editorPanel.goToLinkedButton().setEnabled(hasLinkedNodes());
    }

    private TraceNode findSelectedNode() {
        if (selectedNodeId == null || controller.state().currentDocument() == null) {
            return null;
        }
        return controller.state().currentDocument().nodes().stream()
                .filter(node -> node.id().equals(selectedNodeId))
                .findFirst()
                .orElse(null);
    }

    private List<TraceLink> findAllLinksForSelectedNode() {
        if (selectedNodeId == null || controller.state().currentDocument() == null) {
            return List.of();
        }
        return controller.state().currentDocument().links().stream()
                .filter(link -> selectedNodeId.equals(link.sourceNodeId()) || selectedNodeId.equals(link.targetNodeId()))
                .toList();
    }

    private LinkedNodes findAllLinkedNodes() {
        List<TraceLink> links = findAllLinksForSelectedNode();
        List<TraceNode> sources = new ArrayList<>();
        List<TraceNode> targets = new ArrayList<>();
        for (TraceLink link : links) {
            if (selectedNodeId.equals(link.targetNodeId())) {
                TraceNode sourceNode = findNodeById(link.sourceNodeId());
                if (sourceNode != null) {
                    sources.add(sourceNode);
                }
            }
            if (selectedNodeId.equals(link.sourceNodeId())) {
                TraceNode targetNode = findNodeById(link.targetNodeId());
                if (targetNode != null) {
                    targets.add(targetNode);
                }
            }
        }
        return new LinkedNodes(List.copyOf(sources), List.copyOf(targets));
    }

    private boolean hasLinkedNodes() {
        if (selectedNodeId == null || controller.state().currentDocument() == null) {
            return false;
        }
        return controller.state().currentDocument().links().stream()
                .anyMatch(link -> selectedNodeId.equals(link.sourceNodeId())
                        || selectedNodeId.equals(link.targetNodeId()));
    }

    private TraceNode findNodeById(String nodeId) {
        if (nodeId == null || controller.state().currentDocument() == null) {
            return null;
        }
        return controller.state().currentDocument().nodes().stream()
                .filter(node -> node.id().equals(nodeId))
                .findFirst()
                .orElse(null);
    }

    private void restoreSelection(List<TraceNode> nodes) {
        String preferredSelectedNodeId = controller.consumePreferredSelectedNodeId();
        selectedNodeId = NodeSelectionPolicy.resolveSelectedNodeId(nodes, selectedNodeId, preferredSelectedNodeId);
        int selectedIndex = NodeSelectionPolicy.indexOfNode(nodes, selectedNodeId);
        if (selectedIndex >= 0) {
            editorPanel.nodeList().setSelectedIndex(selectedIndex);
        } else {
            editorPanel.nodeList().clearSelection();
        }
    }

    TraceEditorPanel editorPanel() {
        return editorPanel;
    }

    static List<String> topToolbarButtonLabels() {
        return TOP_TOOLBAR_BUTTON_LABELS;
    }

    private record NodeInput(
            String displayName,
            String qualifiedName,
            String signature,
            String filePath,
            int line,
            String language,
            String note,
            String navigationHint) {
    }

    private record LinkedNodes(
            List<TraceNode> sources,
            List<TraceNode> targets) {
    }
}
