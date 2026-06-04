package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.table.AbstractTableModel;

public final class NodeTableModel extends AbstractTableModel {
    private final List<TraceNode> nodes;
    private final Map<String, String> numberMap;
    private final List<TraceLink> links;

    private static final String[] COLUMN_NAMES = {"编号", "节点名称", "链接关系"};

    public NodeTableModel(List<TraceNode> nodes, Map<String, String> numberMap, List<TraceLink> links) {
        this.nodes = nodes;
        this.numberMap = numberMap;
        this.links = links;
    }

    @Override
    public int getRowCount() {
        return nodes.size();
    }

    @Override
    public int getColumnCount() {
        return 3;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        TraceNode node = nodes.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> numberMap.getOrDefault(node.id(), "");
            case 1 -> node;
            case 2 -> node; // 渲染器会处理链接关系
            default -> null;
        };
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return switch (columnIndex) {
            case 0 -> String.class;
            case 1 -> TraceNode.class;
            case 2 -> TraceNode.class;
            default -> Object.class;
        };
    }

    public TraceNode getNodeAt(int rowIndex) {
        return nodes.get(rowIndex);
    }

    public List<TraceNode> getNodes() {
        return nodes;
    }

    public Map<String, String> getNumberMap() {
        return numberMap;
    }

    public List<TraceLink> getLinks() {
        return links;
    }

    /**
     * 判断指定节点是否有子节点
     */
    public boolean hasChildren(String nodeId) {
        return nodes.stream()
            .anyMatch(node -> nodeId.equals(node.parentId()));
    }

    /**
     * 获取指定节点的所有直接子节点ID列表
     */
    public List<String> getChildrenIds(String parentId) {
        return nodes.stream()
            .filter(node -> parentId.equals(node.parentId()))
            .map(TraceNode::id)
            .toList();
    }

    /**
     * 获取指定节点的所有子孙节点ID（递归）
     */
    public List<String> getDescendantIds(String nodeId) {
        List<String> descendants = new ArrayList<>();
        collectDescendantIds(nodeId, descendants);
        return descendants;
    }

    private void collectDescendantIds(String parentId, List<String> descendants) {
        List<String> childrenIds = getChildrenIds(parentId);
        descendants.addAll(childrenIds);
        for (String childId : childrenIds) {
            collectDescendantIds(childId, descendants);
        }
    }

    /**
     * 获取节点在列表中的索引
     */
    public int getNodeIndex(String nodeId) {
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).id().equals(nodeId)) {
                return i;
            }
        }
        return -1;
    }
}
