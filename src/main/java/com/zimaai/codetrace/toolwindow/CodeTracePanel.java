package com.zimaai.codetrace.toolwindow;

import com.intellij.ui.JBSplitter;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.BorderLayout;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class CodeTracePanel {
    private final CodeTraceController controller;
    private final JPanel root = new JPanel(new BorderLayout());
    private final Map<String, JButton> buttons = new HashMap<>();
    private final TraceFileListPanel fileListPanel = new TraceFileListPanel();
    private final TraceEditorPanel editorPanel = new TraceEditorPanel();
    private boolean syncingTraceNote;
    private boolean syncingNodeNote;
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
        addButton(toolbar, "Refresh", this::refreshAndRepaint);
        addButton(toolbar, "Save Trace Note", this::saveTraceNote);
        addButton(toolbar, "Save Node Note", this::saveNodeNote);
        addButton(toolbar, "Set as Source", this::setSelectedAsSource);
        addButton(toolbar, "Link To Here", this::linkToSelectedNode);
        addButton(toolbar, "Unlink", this::unlinkSelectedNode);

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
            if (event.getValueIsAdjusting()) {
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
    }

    private void addButton(JPanel toolbar, String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(event -> action.run());
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
        selectNode(existing.id());
    }

    private void deleteSelectedNode() {
        if (selectedNodeId == null) {
            return;
        }
        controller.deleteNodeOrPair(selectedNodeId);
        rebuildView();
    }

    private void moveSelectedNode(int offset) {
        if (selectedNodeId == null) {
            return;
        }
        controller.moveNodeOrPair(selectedNodeId, offset);
        rebuildView();
        selectNode(selectedNodeId);
    }

    private void selectNode(String nodeId) {
        var model = editorPanel.nodeList().getModel();
        for (int i = 0; i < model.getSize(); i++) {
            if (model.getElementAt(i).id().equals(nodeId)) {
                editorPanel.nodeList().setSelectedIndex(i);
                return;
            }
        }
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
            editorPanel.nodeList().setListData(new TraceNode[0]);
            selectedNodeId = null;
            syncingNodeNote = true;
            editorPanel.nodeNote().setText("");
            syncingNodeNote = false;
            editorPanel.linkStatus().setText("Link source: none");
            refreshButtons();
            return;
        }

        if (controller.state().currentFileName() != null) {
            fileListPanel.list().setSelectedValue(controller.state().currentFileName(), true);
        }

        syncingTraceNote = true;
        editorPanel.traceNote().setText(document.description() == null ? "" : document.description());
        syncingTraceNote = false;

        editorPanel.nodeList().setListData(document.nodes().toArray(TraceNode[]::new));
        restoreSelection(document.nodes());
        editorPanel.linkStatus().setText("Link source: "
                + (controller.state().pendingLinkSourceId() == null ? "none" : controller.state().pendingLinkSourceId()));

        syncSelectedNodeNote();
        refreshButtons();
    }

    private void syncSelectedNodeNote() {
        TraceNode selected = findSelectedNode();
        syncingNodeNote = true;
        editorPanel.nodeNote().setText(selected == null || selected.note() == null ? "" : selected.note());
        syncingNodeNote = false;
    }

    private void refreshButtons() {
        var document = controller.state().currentDocument();
        boolean hasDocument = document != null;
        boolean hasSelection = findSelectedNode() != null;
        boolean hasPendingSource = controller.state().pendingLinkSourceId() != null;

        if (hasDocument && !syncingTraceNote) {
            String persistedTraceNote = document.description() == null ? "" : document.description();
            editorPanel.saveTraceNoteButton().setEnabled(!persistedTraceNote.equals(editorPanel.traceNote().getText()));
        } else {
            editorPanel.saveTraceNoteButton().setEnabled(false);
        }

        if (hasSelection && !syncingNodeNote) {
            TraceNode selectedNode = findSelectedNode();
            String persistedNodeNote = selectedNode.note() == null ? "" : selectedNode.note();
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

    private void restoreSelection(List<TraceNode> nodes) {
        if (nodes.isEmpty()) {
            selectedNodeId = null;
            return;
        }
        if (selectedNodeId != null) {
            for (int i = 0; i < nodes.size(); i++) {
                if (nodes.get(i).id().equals(selectedNodeId)) {
                    editorPanel.nodeList().setSelectedIndex(i);
                    return;
                }
            }
        }
        selectedNodeId = nodes.get(0).id();
        editorPanel.nodeList().setSelectedIndex(0);
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
}
