package com.zimaai.codetrace.toolwindow;

import com.intellij.icons.AllIcons;
import com.intellij.ui.JBSplitter;
import com.intellij.util.ui.JBUI;
import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class CodeTracePanel {
    private static final List<String> TOP_TOOLBAR_BUTTON_LABELS = List.of("Refresh", "Toggle Files");
    private static final float DEFAULT_FILE_LIST_PROPORTION = 0.25f;
    private static final String FILE_LIST_TOGGLE_BUTTON_NAME = "file-list-toggle-button";
    private static final int ACTION_COLUMN_WIDTH = 112;
    private final CodeTraceController controller;
    private final JPanel root = new JPanel(new BorderLayout());
    private final Map<String, JButton> buttons = new HashMap<>();
    private final TraceFileListPanel fileListPanel = new TraceFileListPanel();
    private final TraceEditorPanel editorPanel = new TraceEditorPanel();
    private final JButton fileListToggleButton = new JButton("<");
    private boolean syncingTraceNote;
    private boolean syncingNodeNote;
    private boolean syncingNodeSelection;
    private boolean fileListCollapsed;
    private float expandedFileListProportion = DEFAULT_FILE_LIST_PROPORTION;
    private String persistedTraceNote = "";
    private String persistedNodeNote = "";
    private String selectedNodeId;
    private JBSplitter split;
    private int[] savedColumnWidths = null;
    private boolean columnWidthsInitialized = false;
    private boolean restoringColumnWidths = false;
    private boolean adjustingFlexibleColumn = false;
    private boolean rebuildingTableModel = false;
    private Predicate<TraceNode> confirmNodeDelete = node -> JOptionPane.showConfirmDialog(
            root,
            "Delete node " + node.displayName() + "?",
            "Confirm Delete",
            JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION;

    public CodeTracePanel(CodeTraceController controller) {
        this.controller = controller;
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
        String preferred = controller.state().preferredSelectedNodeId();
        controller.refreshCurrentFile();
        if (preferred != null) {
            controller.preferSelectedNode(preferred);
        }
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
            if ("Toggle Files".equals(label)) {
                configureFileListToggleButton(toolbar);
            } else {
                addButton(toolbar, label, topToolbarAction(label));
            }
        }

        split = new JBSplitter(false, DEFAULT_FILE_LIST_PROPORTION);
        // Allow users to freely resize file list vs editor list panes.
        split.setHonorComponentsMinimumSize(false);
        split.setDividerWidth(12);
        split.setShowDividerControls(true);
        split.setAllowSwitchOrientationByMouseClick(false);
        split.setFirstComponent(fileListPanel.component());
        split.setSecondComponent(editorPanel.component());

        root.add(toolbar, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        updateFileListToggleButton();
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

        editorPanel.nodeTable().setDragEnabled(true);
        editorPanel.nodeTable().setDropMode(javax.swing.DropMode.INSERT_ROWS);
        editorPanel.nodeTable().setTransferHandler(
                new MultiSelectTransferHandler(controller, this::rebuildView));

        // 添加列宽度监听器，保存用户调整的列宽度
        editorPanel.nodeTable().getColumnModel().addColumnModelListener(new javax.swing.event.TableColumnModelListener() {
            @Override
            public void columnAdded(javax.swing.event.TableColumnModelEvent e) {}
            @Override
            public void columnRemoved(javax.swing.event.TableColumnModelEvent e) {}
            @Override
            public void columnMoved(javax.swing.event.TableColumnModelEvent e) {}
            @Override
            public void columnMarginChanged(javax.swing.event.ChangeEvent e) {
                if (!restoringColumnWidths && !adjustingFlexibleColumn && !rebuildingTableModel) {
                    saveColumnWidths();
                }
                stretchLastColumnToViewport();
            }
            @Override
            public void columnSelectionChanged(javax.swing.event.ListSelectionEvent e) {}
        });

        editorPanel.nodeTable().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                stretchLastColumnToViewport();
            }
        });

        editorPanel.nodeTable().getSelectionModel().addListSelectionListener(event -> {
            if (event.getValueIsAdjusting()) return;
            if (syncingNodeSelection) return;
            syncingNodeSelection = true;
            try {
                int selectedRow = editorPanel.nodeTable().getSelectedRow();
                if (selectedRow < 0) {
                    selectedNodeId = null;
                    controller.clearFocusedNodeId();
                } else {
                    FilteredNodeTableModel model = (FilteredNodeTableModel) editorPanel.nodeTable().getModel();
                    TraceNode selected = model.getNodeAt(selectedRow);
                    selectedNodeId = selected.id();
                    controller.setFocusedNodeId(selectedNodeId);
                }
                // 刷新表格以更新聚焦指示器
                editorPanel.nodeTable().repaint();
                syncSelectedNodeNote();
                refreshButtons();
            } finally {
                syncingNodeSelection = false;
            }
        });

        // Double-click navigation
        editorPanel.nodeTable().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) {
                    int row = editorPanel.nodeTable().rowAtPoint(event.getPoint());
                    if (row >= 0) {
                        FilteredNodeTableModel model = (FilteredNodeTableModel) editorPanel.nodeTable().getModel();
                        TraceNode node = model.getNodeAt(row);
                        controller.navigateToNode(node);
                    }
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
        editorPanel.setAsSourceButton().addActionListener(event -> setSelectedAsSource());
        editorPanel.linkToHereButton().addActionListener(event -> linkToSelectedNode());
        editorPanel.unlinkButton().addActionListener(event -> unlinkSelectedNode());
        editorPanel.goToLinkedButton().addActionListener(event -> goToLinked());

        // 折叠/展开事件监听
        editorPanel.addCollapseExpandListener(nodeId -> {
            TraceDocument doc = controller.state().currentDocument();
            if (doc == null) return;
            boolean isCurrentlyExpanded = doc.expandedNodeIds().contains(nodeId);
            controller.toggleNodeExpand(nodeId, !isCurrentlyExpanded);
            rebuildView();
        });

        // 展开/折叠全部按钮
        editorPanel.expandAllButton().addActionListener(e -> {
            controller.expandAllNodes();
            rebuildView();
        });
        editorPanel.collapseAllButton().addActionListener(e -> {
            controller.collapseAllNodes();
            rebuildView();
        });
    }

    private void addButton(JPanel toolbar, String label, Runnable action) {
        JButton button = new JButton(label, AllIcons.Actions.Refresh);
        button.addActionListener(event -> action.run());
        button.setToolTipText("Reload trace data from disk");
        buttons.put(label, button);
        toolbar.add(button);
    }

    private void configureFileListToggleButton(JPanel toolbar) {
        fileListToggleButton.setName(FILE_LIST_TOGGLE_BUTTON_NAME);
        fileListToggleButton.setFocusable(false);
        fileListToggleButton.setMargin(JBUI.insets(2));
        fileListToggleButton.addActionListener(event -> toggleFileList());
        toolbar.add(fileListToggleButton);
    }

    private void toggleFileList() {
        if (split == null) {
            return;
        }
        if (fileListCollapsed) {
            fileListCollapsed = false;
            split.setProportion(expandedFileListProportion);
        } else {
            float currentProportion = split.getProportion();
            if (currentProportion > 0.0f) {
                expandedFileListProportion = currentProportion;
            }
            fileListCollapsed = true;
            split.setProportion(0.0f);
        }
        updateFileListToggleButton();
        split.revalidate();
        split.repaint();
    }

    private void updateFileListToggleButton() {
        fileListToggleButton.setText(fileListCollapsed ? ">" : "<");
        fileListToggleButton.setToolTipText(fileListCollapsed ? "Expand file list" : "Collapse file list");
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

    private void handleRowAction(NodeRowAction action, TraceNode node) {
        selectNode(node);
        switch (action) {
            case EDIT -> editNode(node);
            case DELETE -> deleteNode(node);
            case MOVE_UP -> moveNode(node, -1);
            case MOVE_DOWN -> moveNode(node, 1);
        }
    }

    private void selectNode(TraceNode node) {
        selectedNodeId = node.id();
        controller.setFocusedNodeId(selectedNodeId);
    }

    private void editNode(TraceNode existing) {
        if (existing == null) return;
        NodeInput input = showNodeDialog("Edit Node", existing);
        if (input == null) return;
        TraceNode updated = new TraceNode(
                existing.id(),
                input.displayName(),
                input.qualifiedName(),
                input.signature(),
                input.filePath(),
                input.line(),
                input.language(),
                input.note(),
                input.navigationHint(),
                existing.parentId(),
                input.title());
        controller.updateNode(updated);
        rebuildView();
    }

    private void deleteNode(TraceNode node) {
        if (node == null || !confirmNodeDelete.test(node)) {
            return;
        }
        selectedNodeId = null;
        controller.clearFocusedNodeId();
        controller.deleteNode(node.id());
        rebuildView();
    }

    private void moveNode(TraceNode node, int offset) {
        if (node == null) {
            return;
        }
        selectedNodeId = node.id();
        controller.moveNode(node.id(), offset);
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
        // 在表格中查找并选中节点
        FilteredNodeTableModel model = (FilteredNodeTableModel) editorPanel.nodeTable().getModel();
        if (model == null) return;
        int idx = model.getVisibleIndex(node.id());
        if (idx >= 0) {
            editorPanel.nodeTable().setRowSelectionInterval(idx, idx);
            editorPanel.nodeTable().scrollRectToVisible(editorPanel.nodeTable().getCellRect(idx, 0, true));
        }
    }


    private NodeInput showNodeDialog(String title, TraceNode initial) {
        javax.swing.JTextField titleField = new javax.swing.JTextField(initial == null || initial.title() == null ? "" : initial.title());
        javax.swing.JTextField nameField = new javax.swing.JTextField(initial == null ? "" : initial.displayName());
        javax.swing.JTextField qualifiedField = new javax.swing.JTextField(initial == null ? "" : initial.qualifiedName());
        javax.swing.JTextField signatureField = new javax.swing.JTextField(initial == null ? "" : initial.signature());
        javax.swing.JTextField fileField = new javax.swing.JTextField(initial == null ? "" : initial.filePath());
        javax.swing.JTextField lineField = new javax.swing.JTextField(initial == null ? "1" : Integer.toString(initial.line()));
        javax.swing.JTextField languageField = new javax.swing.JTextField(initial == null ? "UNKNOWN" : initial.language());
        javax.swing.JTextField hintField = new javax.swing.JTextField(initial == null ? "" : initial.navigationHint());
        javax.swing.JTextField noteField = new javax.swing.JTextField(initial == null ? "" : initial.note());

        JPanel panel = new JPanel(new java.awt.GridLayout(0, 1));
        panel.add(new javax.swing.JLabel("Title"));
        panel.add(titleField);
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
                titleField.getText().trim(),
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
            editorPanel.nodeTable().setModel(new javax.swing.table.DefaultTableModel());
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
        rebuildingTableModel = true;
        try {
            // 计算编号
            Map<String, String> numberMap = NodeNumberingService.calculateNumbers(document);
            // 创建表格模型
            NodeTableModel tableModel = new NodeTableModel(document.nodes(), numberMap, document.links());
            // 配置折叠支持（设置FilteredNodeTableModel和折叠渲染器）
            editorPanel.configureTableWithCollapseSupport(tableModel, document);
            // 设置其他列渲染器
            editorPanel.nodeTable().getColumnModel().getColumn(1).setCellRenderer(
                    new NodeNameRenderer(
                            () -> controller.state().currentDocument(),
                            () -> controller.state().focusedNodeId(),
                            () -> controller.state().pendingLinkSourceId()));
            editorPanel.nodeTable().getColumnModel().getColumn(2).setResizable(false);
            editorPanel.nodeTable().getColumnModel().getColumn(2).setCellRenderer(
                    new LinkRelationColumnRenderer(
                            () -> controller.state().currentDocument(),
                            () -> numberMap,
                            () -> controller.state().focusedNodeId(),
                            () -> controller.state().pendingLinkSourceId()));
            configureActionColumn();
            // 设置列宽比例 编号:节点名称:链接关系 = 1:5:1
            // 只在首次加载时设置默认宽度，之后恢复用户调整的宽度
            if (!columnWidthsInitialized) {
                int totalWidth = editorPanel.nodeTable().getWidth();
                if (totalWidth <= 0) {
                    totalWidth = 560; // 默认总宽度
                }
                int flexibleWidth = Math.max(420, totalWidth - ACTION_COLUMN_WIDTH);
                int unit = flexibleWidth / 7;
                editorPanel.nodeTable().getColumnModel().getColumn(0).setPreferredWidth(unit);
                editorPanel.nodeTable().getColumnModel().getColumn(0).setWidth(unit);
                editorPanel.nodeTable().getColumnModel().getColumn(1).setPreferredWidth(unit * 5);
                editorPanel.nodeTable().getColumnModel().getColumn(1).setWidth(unit * 5);
                columnWidthsInitialized = true;
                saveColumnWidths();
                stretchLastColumnToViewport();
            } else {
                restoreColumnWidths();
            }
            restoreSelection(document.nodes());
        } finally {
            rebuildingTableModel = false;
            syncingNodeSelection = false;
        }
        editorPanel.linkStatus().setText("Link source: "
                + (controller.state().pendingLinkSourceId() == null ? "none" : controller.state().pendingLinkSourceId()));

        syncSelectedNodeNote();
        refreshButtons();
    }

    private void configureActionColumn() {
        javax.swing.table.TableColumn actionColumn = editorPanel.nodeTable().getColumnModel().getColumn(3);
        actionColumn.setMinWidth(ACTION_COLUMN_WIDTH);
        actionColumn.setPreferredWidth(ACTION_COLUMN_WIDTH);
        actionColumn.setMaxWidth(ACTION_COLUMN_WIDTH);
        actionColumn.setWidth(ACTION_COLUMN_WIDTH);
        actionColumn.setResizable(false);
        actionColumn.setCellRenderer(new NodeRowActionsRenderer());
        actionColumn.setCellEditor(new NodeRowActionsEditor(this::handleRowAction));
    }

    private void saveColumnWidths() {
        if (editorPanel.nodeTable().getColumnModel().getColumnCount() >= 4) {
            savedColumnWidths = new int[] {
                editorPanel.nodeTable().getColumnModel().getColumn(0).getWidth(),
                editorPanel.nodeTable().getColumnModel().getColumn(1).getWidth()
            };
        }
    }

    private void restoreColumnWidths() {
        if (savedColumnWidths != null
                && savedColumnWidths.length >= 2
                && editorPanel.nodeTable().getColumnModel().getColumnCount() >= 4) {
            restoringColumnWidths = true;
            try {
                editorPanel.nodeTable().getColumnModel().getColumn(0).setWidth(savedColumnWidths[0]);
                editorPanel.nodeTable().getColumnModel().getColumn(0).setPreferredWidth(savedColumnWidths[0]);
                editorPanel.nodeTable().getColumnModel().getColumn(1).setWidth(savedColumnWidths[1]);
                editorPanel.nodeTable().getColumnModel().getColumn(1).setPreferredWidth(savedColumnWidths[1]);
            } finally {
                restoringColumnWidths = false;
            }
            stretchLastColumnToViewport();
        }
    }

    private void stretchLastColumnToViewport() {
        JTable table = editorPanel.nodeTable();
        if (table.getColumnModel().getColumnCount() < 4) {
            return;
        }
        if (!(table.getParent() instanceof JViewport viewport)) {
            return;
        }
        int viewportWidth = viewport.getWidth();
        if (viewportWidth <= 0) {
            return;
        }

        int firstWidth = table.getColumnModel().getColumn(0).getWidth();
        int secondWidth = table.getColumnModel().getColumn(1).getWidth();
        int actionWidth = table.getColumnModel().getColumn(3).getWidth();
        int linkWidth = Math.max(0, viewportWidth - firstWidth - secondWidth - actionWidth);

        adjustingFlexibleColumn = true;
        try {
            table.getColumnModel().getColumn(2).setWidth(linkWidth);
            table.getColumnModel().getColumn(2).setPreferredWidth(linkWidth);
        } finally {
            adjustingFlexibleColumn = false;
        }
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
        int selectedRow = editorPanel.nodeTable().getSelectedRow();
        if (selectedRow >= 0) {
            FilteredNodeTableModel model = (FilteredNodeTableModel) editorPanel.nodeTable().getModel();
            TraceNode node = model.getNodeAt(selectedRow);
            if (node.id().equals(selectedNodeId)) {
                return node;
            }
        }
        // fallback: search document
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
        TraceDocument document = controller.state().currentDocument();
        Set<String> nodeIds = document != null
                ? document.nodes().stream().map(TraceNode::id).collect(Collectors.toSet())
                : Set.of();
        List<TraceNode> sources = new ArrayList<>();
        List<TraceNode> targets = new ArrayList<>();
        for (TraceLink link : links) {
            if (document != null && !TraceDocumentEditor.isLinkValid(link, nodeIds)) {
                continue;
            }
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
        TraceDocument document = controller.state().currentDocument();
        Set<String> nodeIds = document.nodes().stream()
                .map(TraceNode::id)
                .collect(Collectors.toSet());
        return document.links().stream()
                .anyMatch(link -> TraceDocumentEditor.isLinkValid(link, nodeIds)
                        && (selectedNodeId.equals(link.sourceNodeId())
                                || selectedNodeId.equals(link.targetNodeId())));
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
        if (selectedNodeId == null) {
            controller.clearFocusedNodeId();
            editorPanel.nodeTable().clearSelection();
            return;
        }
        controller.setFocusedNodeId(selectedNodeId);
        // 使用过滤模型查找可见行
        FilteredNodeTableModel filteredModel = editorPanel.getFilteredModel();
        if (filteredModel == null) return;
        int visibleIndex = filteredModel.getVisibleIndex(selectedNodeId);
        if (visibleIndex >= 0) {
            editorPanel.nodeTable().setRowSelectionInterval(visibleIndex, visibleIndex);
            editorPanel.nodeTable().scrollRectToVisible(editorPanel.nodeTable().getCellRect(visibleIndex, 0, true));
        } else {
            // 节点不可见（被折叠）
            selectedNodeId = null;
            controller.clearFocusedNodeId();
            editorPanel.nodeTable().clearSelection();
        }
    }

    TraceEditorPanel editorPanel() {
        return editorPanel;
    }

    void setConfirmNodeDeleteForTest(Predicate<TraceNode> confirmNodeDelete) {
        this.confirmNodeDelete = Objects.requireNonNull(confirmNodeDelete, "confirmNodeDelete");
    }

    static List<String> topToolbarButtonLabels() {
        return TOP_TOOLBAR_BUTTON_LABELS;
    }

    private record NodeInput(
            String title,
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

    private static class NodeNumberRenderer extends DefaultTableCellRenderer {
        @Override
        public java.awt.Component getTableCellRendererComponent(
                javax.swing.JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            javax.swing.JLabel label = (javax.swing.JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            label.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
            return label;
        }
    }

    private static class NodeNameRenderer extends DefaultTableCellRenderer {
        private final java.util.function.Supplier<TraceDocument> documentSupplier;
        private final java.util.function.Supplier<String> focusedNodeIdSupplier;
        private final java.util.function.Supplier<String> pendingSourceSupplier;

        public NodeNameRenderer(
                java.util.function.Supplier<TraceDocument> documentSupplier,
                java.util.function.Supplier<String> focusedNodeIdSupplier,
                java.util.function.Supplier<String> pendingSourceSupplier) {
            this.documentSupplier = documentSupplier;
            this.focusedNodeIdSupplier = focusedNodeIdSupplier;
            this.pendingSourceSupplier = pendingSourceSupplier;
        }

        @Override
        public java.awt.Component getTableCellRendererComponent(
                javax.swing.JTable table, Object value, boolean isSelected, boolean hasFocus,
                int row, int column) {
            javax.swing.JLabel label = (javax.swing.JLabel) super.getTableCellRendererComponent(
                    table, "", isSelected, hasFocus, row, column);

            if (!(value instanceof TraceNode node) || node == TraceTreeModel.VIRTUAL_ROOT) {
                return label;
            }

            label.setOpaque(true);
            label.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 4, 3, 4));

            StringBuilder prefix = new StringBuilder();

            // Focus indicator
            if (node.id().equals(focusedNodeIdSupplier.get())) {
                prefix.append("● ");
            }

            // Title + displayName
            String title = node.title();
            if (title != null && !title.isBlank()) {
                String truncated = title.length() > 15
                        ? title.substring(0, 15) + "…" : title;
                label.setText(prefix + truncated + " — " + node.displayName());
            } else {
                label.setText(prefix + node.displayName());
            }

            return label;
        }
    }
}
