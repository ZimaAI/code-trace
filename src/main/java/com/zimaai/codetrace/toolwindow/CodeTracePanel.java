package com.zimaai.codetrace.toolwindow;

import com.intellij.openapi.project.Project;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBTextArea;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class CodeTracePanel {
    private final CodeTraceController controller;
    private final JPanel root = new JPanel(new BorderLayout());
    private final Map<String, JButton> buttons = new HashMap<>();
    private final TraceFileListPanel fileListPanel;
    private final TraceEditorPanel editorPanel;
    private final HistoryListPanel historyPanel;

    public CodeTracePanel(Project project, CodeTraceController controller) {
        this.controller = controller;

        this.fileListPanel = new TraceFileListPanel();
        this.editorPanel = new TraceEditorPanel();
        this.historyPanel = new HistoryListPanel();
        configureLeftPaneActions();
        wireSelection();
        wireNoteEditor();
        wireNodeNavigation();

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
                var document = controller.state().currentDocument();
                if (document == null || document.current() == null || index < 0 || index >= document.current().nodes().size()) {
                    return;
                }
                controller.navigateToNode(document.current().nodes().get(index));
            }
        });
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

    private void refreshHistory() {
        var document = controller.state().currentDocument();
        if (document == null) {
            historyPanel.list().setListData(new String[0]);
            return;
        }
        String[] entries = document.history().stream()
                .map(version -> version.versionId() + " (" + version.source() + ")")
                .toArray(String[]::new);
        historyPanel.list().setListData(entries);
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
                if (document.current() != null) {
                    String[] nodes = document.current().nodes().stream()
                            .map(node -> node.displayName() + " @ " + node.filePath() + ":" + node.line())
                            .toArray(String[]::new);
                    editorPanel.nodeList().setListData(nodes);
                } else {
                    editorPanel.nodeList().setListData(new String[0]);
                }
            } else {
                editorPanel.traceNote().setText("");
                editorPanel.nodeList().setListData(new String[0]);
            }

            refreshHistory();
        });
    }
}
