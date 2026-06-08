package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import javax.swing.table.AbstractTableModel;
import java.util.*;

/**
 * 过滤节点表格模型，根据折叠状态过滤可见节点
 */
public class FilteredNodeTableModel extends AbstractTableModel {
    private final NodeTableModel sourceModel;
    private TraceDocument document;
    private List<TraceNode> visibleNodes;
    private Map<String, Integer> nodeIdToVisibleIndex;

    public FilteredNodeTableModel(NodeTableModel sourceModel, TraceDocument document) {
        this.sourceModel = Objects.requireNonNull(sourceModel, "sourceModel");
        this.document = Objects.requireNonNull(document, "document");
        this.visibleNodes = new ArrayList<>();
        this.nodeIdToVisibleIndex = new HashMap<>();
        rebuildVisibleNodes();
    }

    /**
     * 设置新的文档并重建可见节点
     */
    public void setDocument(TraceDocument document) {
        this.document = Objects.requireNonNull(document, "document");
        rebuildVisibleNodes();
    }

    /**
     * 重建可见节点列表
     */
    public void rebuildVisibleNodes() {
        visibleNodes.clear();
        nodeIdToVisibleIndex.clear();

        Set<String> expandedNodeIds = document.expandedNodeIds();
        List<TraceNode> allNodes = sourceModel.getNodes();

        for (TraceNode node : allNodes) {
            if (isVisible(node, expandedNodeIds, allNodes)) {
                nodeIdToVisibleIndex.put(node.id(), visibleNodes.size());
                visibleNodes.add(node);
            }
        }

        fireTableDataChanged();
    }

    /**
     * 判断节点是否可见
     */
    public boolean isVisible(TraceNode node) {
        return isVisible(node, document.expandedNodeIds(), sourceModel.getNodes());
    }

    private boolean isVisible(TraceNode node, Set<String> expandedNodeIds, List<TraceNode> allNodes) {
        if (node.parentId() == null) {
            return true;
        }

        String currentParentId = node.parentId();
        while (currentParentId != null) {
            // If parent is NOT expanded, child is hidden
            if (!expandedNodeIds.contains(currentParentId)) {
                return false;
            }

            TraceNode parentNode = findNodeById(allNodes, currentParentId);
            if (parentNode == null) {
                return true;
            }

            currentParentId = parentNode.parentId();
        }

        return true;
    }

    private TraceNode findNodeById(List<TraceNode> nodes, String nodeId) {
        for (TraceNode node : nodes) {
            if (node.id().equals(nodeId)) {
                return node;
            }
        }
        return null;
    }

    /**
     * 获取节点在可见列表中的索引
     */
    public int getVisibleIndex(String nodeId) {
        return nodeIdToVisibleIndex.getOrDefault(nodeId, -1);
    }

    /**
     * 获取指定索引的节点
     */
    public TraceNode getNodeAt(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= visibleNodes.size()) {
            throw new IndexOutOfBoundsException(
                "Row index out of bounds: " + rowIndex + ", size: " + visibleNodes.size());
        }
        return visibleNodes.get(rowIndex);
    }

    /**
     * 获取所有可见节点
     */
    public List<TraceNode> getVisibleNodes() {
        return Collections.unmodifiableList(visibleNodes);
    }

    /**
     * 获取节点的层级深度
     */
    public int getNodeDepth(TraceNode node) {
        int depth = 0;
        String parentId = node.parentId();
        List<TraceNode> allNodes = sourceModel.getNodes();

        while (parentId != null) {
            depth++;
            TraceNode parentNode = findNodeById(allNodes, parentId);
            if (parentNode == null) {
                break;
            }
            parentId = parentNode.parentId();
        }

        return depth;
    }

    /**
     * 判断节点是否是其父节点的最后一个子节点
     */
    public boolean isLastChild(TraceNode node) {
        if (node.parentId() == null) {
            List<TraceNode> allNodes = sourceModel.getNodes();
            for (int i = allNodes.size() - 1; i >= 0; i--) {
                if (allNodes.get(i).parentId() == null) {
                    return allNodes.get(i).id().equals(node.id());
                }
            }
            return true;
        }

        List<String> siblings = sourceModel.getChildrenIds(node.parentId());
        if (siblings.isEmpty()) {
            return true;
        }
        return siblings.get(siblings.size() - 1).equals(node.id());
    }

    // AbstractTableModel 方法实现

    @Override
    public int getRowCount() {
        return visibleNodes.size();
    }

    @Override
    public int getColumnCount() {
        return sourceModel.getColumnCount();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        TraceNode node = visibleNodes.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> sourceModel.getNumberMap().getOrDefault(node.id(), "");
            case 1 -> node;
            case 2 -> node;
            case 3 -> node;
            default -> null;
        };
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return sourceModel.getColumnClass(columnIndex);
    }

    @Override
    public String getColumnName(int column) {
        return sourceModel.getColumnName(column);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return sourceModel.isCellEditable(rowIndex, columnIndex);
    }

    /**
     * 获取源模型
     */
    public NodeTableModel getSourceModel() {
        return sourceModel;
    }

    /**
     * 获取当前文档
     */
    public TraceDocument getDocument() {
        return document;
    }
}
