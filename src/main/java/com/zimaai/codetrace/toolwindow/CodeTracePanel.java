package com.zimaai.codetrace.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextArea;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.model.TraceVersion;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class CodeTracePanel {
    private final CodeTraceController controller;
    private final JPanel root = new JPanel(new BorderLayout());
    private final Map<String, JButton> buttons = new HashMap<>();
    private final TraceFileListPanel fileListPanel;
    private final TraceEditorPanel editorPanel;
    private final HistoryListPanel historyPanel;
    private boolean showingHistoryVersion;
    private int selectedHistoryIndex = -1;

    public CodeTracePanel(Project project, CodeTraceController controller) {
        this.controller = controller;

        this.fileListPanel = new TraceFileListPanel();
        this.editorPanel = new TraceEditorPanel();
        this.historyPanel = new HistoryListPanel();
        configureLeftPaneActions();
        wireSelection();
        wireNoteEditor();
        wireNodeNavigation();
        wireHistorySelection();
        wireNodeNoteEditor();
        wireNodeActions();

        JPanel toolbar = new JPanel();
        addButton(toolbar, "Start Recording", controller::startRecording);
        addButton(toolbar, "Stop Recording", controller::stopRecording);
        addButton(toolbar, "Save", controller::saveCurrentFile);
        addButton(toolbar, "Refresh", this::refreshAndRepaint);

        JBSplitter right = new JBSplitter(true, 0.75f);
        right.setFirstComponent(editorPanel.component());
        right.setSecondComponent(historyPanel.component());

        JBSplitter split = new JBSplitter(false, 0.25f);
        split.setFirstComponent(fileListPanel.component());
        split.setSecondComponent(right);

        root.add(toolbar, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
    }

    public JComponent getComponent() {
        return root;
    }

    public JButton findButton(String text) {
        return buttons.get(text);
    }

    public void reloadFromDisk() {
        controller.ensureAnyFileLoaded();
        showingHistoryVersion = false;
        selectedHistoryIndex = -1;
        rebuildView();
    }

    public com.zimaai.codetrace.model.TraceDocument currentDocument() {
        return controller.state().currentDocument();
    }

    private void addButton(JPanel toolbar, String label, Runnable action) {
        JButton button = new JButton(label);
        button.addActionListener(event -> action.run());
        buttons.put(label, button);
        toolbar.add(button);
    }

    private void configureLeftPaneActions() {
        fileListPanel.configureActions(
                this::createFile,
                this::renameSelectedFile,
                this::copySelectedFile,
                this::deleteSelectedFile,
                this::refreshAndRepaint);
    }

    private void wireSelection() {
        JBList<String> list = fileListPanel.list();
        list.addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            String selected = list.getSelectedValue();
            if (selected == null || selected.equals(controller.state().currentFileName())) {
                return;
            }
            controller.load(selected);
            rebuildView();
        });
    }

    private void wireNoteEditor() {
        JBTextArea note = editorPanel.traceNote();
        note.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateTraceNote();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateTraceNote();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateTraceNote();
            }

            private void updateTraceNote() {
                if (!note.hasFocus()) {
                    return;
                }
                controller.updateDescription(note.getText());
                refreshHistory();
            }
        });
    }

    private void wireNodeNavigation() {
        editorPanel.nodeList().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() != 2) {
                    return;
                }
                int index = editorPanel.nodeList().locationToIndex(event.getPoint());
                TraceVersion version = getDisplayedVersion();
                if (version == null || index < 0 || index >= version.nodes().size()) {
                    return;
                }
                controller.navigateToNode(version.nodes().get(index));
            }
        });
    }

    private void wireHistorySelection() {
        historyPanel.list().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent event) {
                if (event.getValueIsAdjusting()) {
                    return;
                }
                int index = historyPanel.list().getSelectedIndex();
                if (index <= 0) {
                    showingHistoryVersion = false;
                    selectedHistoryIndex = -1;
                } else {
                    showingHistoryVersion = true;
                    selectedHistoryIndex = index - 1;
                }
                rebuildNodeListAndNodeNote();
            }
        });
    }

    private void wireNodeNoteEditor() {
        editorPanel.nodeList().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) {
                return;
            }
            refreshSelectedNodeNote();
        });

        JBTextArea nodeNote = editorPanel.nodeNote();
        nodeNote.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updateNodeNote();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updateNodeNote();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updateNodeNote();
            }

            private void updateNodeNote() {
                if (!nodeNote.hasFocus() || showingHistoryVersion) {
                    return;
                }
                int index = editorPanel.nodeList().getSelectedIndex();
                if (index < 0) {
                    return;
                }
                controller.updateNodeNote(index, nodeNote.getText());
                refreshHistory();
            }
        });
    }

    private void wireNodeActions() {
        editorPanel.addNodeButton().addActionListener(event -> addNode());
        editorPanel.editNodeButton().addActionListener(event -> editSelectedNode());
        editorPanel.deleteNodeButton().addActionListener(event -> deleteSelectedNode());
        editorPanel.moveUpButton().addActionListener(event -> moveSelectedNode(-1));
        editorPanel.moveDownButton().addActionListener(event -> moveSelectedNode(1));
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
        showingHistoryVersion = false;
        selectedHistoryIndex = -1;
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
        showingHistoryVersion = false;
        selectedHistoryIndex = -1;
        rebuildView();
    }

    private void refreshAndRepaint() {
        if (controller.hasUnsavedChanges()) {
            UnsavedChangesDecision decision = askRefreshDecision();
            controller.recordDecision(decision);
            if (!controller.refreshWithDecision(decision)) {
                return;
            }
        } else {
            controller.refreshCurrentFile();
        }
        showingHistoryVersion = false;
        selectedHistoryIndex = -1;
        rebuildView();
    }

    private UnsavedChangesDecision askRefreshDecision() {
        Object[] options = {"Save and Refresh", "Discard and Refresh", "Cancel"};
        int choice = JOptionPane.showOptionDialog(
                root,
                "There are unsaved changes. Choose how to continue refresh.",
                "Unsaved Changes",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                options,
                options[0]);
        return switch (choice) {
            case 0 -> UnsavedChangesDecision.SAVE;
            case 1 -> UnsavedChangesDecision.DISCARD;
            default -> UnsavedChangesDecision.CANCEL;
        };
    }

    private void addNode() {
        if (showingHistoryVersion) {
            return;
        }
        NodeInput input = showNodeDialog("Add Node", null);
        if (input == null) {
            return;
        }
        TraceNode node = new TraceNode(
                "manual-" + System.nanoTime(),
                input.displayName(),
                input.qualifiedName(),
                input.signature(),
                input.filePath(),
                input.line(),
                input.language(),
                input.note(),
                input.navigationHint());
        int index = controller.addNode(node);
        rebuildView();
        if (index >= 0) {
            editorPanel.nodeList().setSelectedIndex(index);
        }
    }

    private void editSelectedNode() {
        if (showingHistoryVersion) {
            return;
        }
        int index = editorPanel.nodeList().getSelectedIndex();
        TraceVersion version = getDisplayedVersion();
        if (version == null || index < 0 || index >= version.nodes().size()) {
            return;
        }
        TraceNode existing = version.nodes().get(index);
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
        controller.updateNode(index, updated);
        rebuildView();
        editorPanel.nodeList().setSelectedIndex(index);
    }

    private void deleteSelectedNode() {
        if (showingHistoryVersion) {
            return;
        }
        int index = editorPanel.nodeList().getSelectedIndex();
        if (index < 0) {
            return;
        }
        controller.deleteNode(index);
        rebuildView();
        int nextIndex = Math.max(0, index - 1);
        if (editorPanel.nodeList().getModel().getSize() > 0) {
            editorPanel.nodeList().setSelectedIndex(Math.min(nextIndex, editorPanel.nodeList().getModel().getSize() - 1));
        }
    }

    private void moveSelectedNode(int offset) {
        if (showingHistoryVersion) {
            return;
        }
        int index = editorPanel.nodeList().getSelectedIndex();
        if (index < 0) {
            return;
        }
        int movedTo = controller.moveNode(index, offset);
        rebuildView();
        if (movedTo >= 0 && movedTo < editorPanel.nodeList().getModel().getSize()) {
            editorPanel.nodeList().setSelectedIndex(movedTo);
        }
    }

    private NodeInput showNodeDialog(String title, TraceNode initial) {
        JBTextArea fileField = new JBTextArea(initial == null ? "" : initial.filePath());
        javax.swing.JTextField nameField = new javax.swing.JTextField(initial == null ? "" : initial.displayName());
        javax.swing.JTextField qualifiedField = new javax.swing.JTextField(initial == null ? "" : initial.qualifiedName());
        javax.swing.JTextField signatureField = new javax.swing.JTextField(initial == null ? "" : initial.signature());
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
                Math.max(line, 1),
                languageField.getText().trim().isEmpty() ? "UNKNOWN" : languageField.getText().trim(),
                noteField.getText(),
                hintField.getText().trim());
    }

    private void refreshHistory() {
        var document = controller.state().currentDocument();
        if (document == null) {
            historyPanel.list().setListData(new String[0]);
            return;
        }
        String[] entries = new String[document.history().size() + 1];
        entries[0] = "Current: " + (document.current() == null ? "-" : document.current().versionId());
        for (int i = 0; i < document.history().size(); i++) {
            var version = document.history().get(i);
            entries[i + 1] = "History: " + version.versionId() + " (" + version.source() + ")";
        }
        historyPanel.list().setListData(entries);
        if (showingHistoryVersion && selectedHistoryIndex >= 0 && selectedHistoryIndex + 1 < entries.length) {
            historyPanel.list().setSelectedIndex(selectedHistoryIndex + 1);
        } else {
            historyPanel.list().setSelectedIndex(0);
        }
    }

    private TraceVersion getDisplayedVersion() {
        var document = controller.state().currentDocument();
        if (document == null) {
            return null;
        }
        if (showingHistoryVersion) {
            if (selectedHistoryIndex < 0 || selectedHistoryIndex >= document.history().size()) {
                return null;
            }
            return document.history().get(selectedHistoryIndex);
        }
        return document.current();
    }

    private void rebuildNodeListAndNodeNote() {
        TraceVersion version = getDisplayedVersion();
        if (version == null) {
            editorPanel.nodeList().setListData(new String[0]);
            editorPanel.nodeNote().setText("");
            editorPanel.nodeNote().setEditable(false);
            setNodeActionEnabled(false);
            return;
        }
        String[] nodes = version.nodes().stream()
                .map(node -> node.displayName() + " @ " + node.filePath() + ":" + node.line())
                .toArray(String[]::new);
        editorPanel.nodeList().setListData(nodes);
        editorPanel.nodeNote().setEditable(!showingHistoryVersion);
        setNodeActionEnabled(!showingHistoryVersion);
        if (nodes.length > 0) {
            editorPanel.nodeList().setSelectedIndex(0);
        } else {
            editorPanel.nodeNote().setText("");
        }
    }

    private void refreshSelectedNodeNote() {
        int index = editorPanel.nodeList().getSelectedIndex();
        TraceVersion version = getDisplayedVersion();
        if (version == null || index < 0 || index >= version.nodes().size()) {
            editorPanel.nodeNote().setText("");
            return;
        }
        String note = version.nodes().get(index).note();
        editorPanel.nodeNote().setText(note == null ? "" : note);
    }

    private void rebuildView() {
        SwingUtilities.invokeLater(() -> {
            List<String> files = controller.loadFileNames();
            fileListPanel.list().setListData(files.toArray(String[]::new));

            var document = controller.state().currentDocument();
            if (document != null) {
                if (controller.state().currentFileName() != null) {
                    fileListPanel.list().setSelectedValue(controller.state().currentFileName(), true);
                }
                editorPanel.traceNote().setText(document.description() == null ? "" : document.description());
            } else {
                editorPanel.traceNote().setText("");
            }

            refreshHistory();
            rebuildNodeListAndNodeNote();
        });
    }

    private void setNodeActionEnabled(boolean enabled) {
        editorPanel.addNodeButton().setEnabled(enabled);
        editorPanel.editNodeButton().setEnabled(enabled);
        editorPanel.deleteNodeButton().setEnabled(enabled);
        editorPanel.moveUpButton().setEnabled(enabled);
        editorPanel.moveDownButton().setEnabled(enabled);
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
