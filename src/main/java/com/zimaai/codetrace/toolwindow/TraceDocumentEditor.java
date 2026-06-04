package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public final class TraceDocumentEditor {
    public TraceDocument saveDescription(TraceDocument document, String description, Instant now) {
        return new TraceDocument(
                3,
                document.id(),
                document.name(),
                description,
                document.createdAt(),
                now,
                document.nodes(),
                document.links(),
                document.expandedNodeIds());
    }

    public TraceDocument saveNodeNote(TraceDocument document, String nodeId, String note, Instant now) {
        List<TraceNode> updatedNodes = new ArrayList<>();
        for (TraceNode node : document.nodes()) {
            if (node.id().equals(nodeId)) {
                updatedNodes.add(new TraceNode(
                        node.id(),
                        node.displayName(),
                        node.qualifiedName(),
                        node.signature(),
                        node.filePath(),
                        node.line(),
                        node.language(),
                        note,
                        node.navigationHint(),
                        node.parentId(),
                        node.title()));
            } else {
                updatedNodes.add(node);
            }
        }
        return new TraceDocument(
                3,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                List.copyOf(updatedNodes),
                document.links(),
                document.expandedNodeIds());
    }

    public TraceDocument addNode(TraceDocument document, TraceNode node, Instant now) {
        List<TraceNode> nodes = new ArrayList<>(document.nodes());
        nodes.add(node);
        return new TraceDocument(
                3,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                List.copyOf(nodes),
                document.links(),
                document.expandedNodeIds());
    }

    public TraceDocument insertNodeAt(TraceDocument document, TraceNode node, int index, Instant now) {
        List<TraceNode> nodes = new ArrayList<>(document.nodes());
        int boundedIndex = Math.max(0, Math.min(index, nodes.size()));
        nodes.add(boundedIndex, node);
        return new TraceDocument(
                3,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                List.copyOf(nodes),
                document.links(),
                document.expandedNodeIds());
    }

    public TraceDocument updateNode(TraceDocument document, TraceNode replacement, Instant now) {
        List<TraceNode> nodes = new ArrayList<>(document.nodes());
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).id().equals(replacement.id())) {
                nodes.set(i, replacement);
                break;
            }
        }
        return new TraceDocument(
                3,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                List.copyOf(nodes),
                document.links(),
                document.expandedNodeIds());
    }

    /**
     * Returns true if both endpoints of the link exist in the given node-id set.
     * Prefer this overload in loops to avoid rebuilding the set on every call.
     */
    public static boolean isLinkValid(TraceLink link, Set<String> nodeIds) {
        return nodeIds.contains(link.sourceNodeId()) && nodeIds.contains(link.targetNodeId());
    }

    public TraceDocument link(TraceDocument document, String sourceNodeId, String targetNodeId, TraceLinkKind kind, Instant now) {
        Objects.requireNonNull(sourceNodeId, "sourceNodeId");
        Objects.requireNonNull(targetNodeId, "targetNodeId");
        if (sourceNodeId.equals(targetNodeId)) {
            throw new IllegalArgumentException("Cannot link a node to itself");
        }
        Set<String> nodeIds = document.nodes().stream()
                .map(TraceNode::id)
                .collect(Collectors.toSet());
        for (TraceLink link : document.links()) {
            if (!isLinkValid(link, nodeIds)) {
                continue; // skip dangling link that references a deleted node
            }
            if (link.sourceNodeId().equals(sourceNodeId)
                    || link.targetNodeId().equals(sourceNodeId)
                    || link.sourceNodeId().equals(targetNodeId)
                    || link.targetNodeId().equals(targetNodeId)) {
                throw new IllegalArgumentException("Each node can participate in at most one link");
            }
        }
        List<TraceLink> links = new ArrayList<>(document.links());
        links.add(new TraceLink("link-" + UUID.randomUUID(), sourceNodeId, targetNodeId, now, kind));
        return new TraceDocument(
                3,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                document.nodes(),
                List.copyOf(links),
                document.expandedNodeIds());
    }

    public TraceDocument unlink(TraceDocument document, String nodeId, Instant now) {
        List<TraceLink> links = document.links().stream()
                .filter(link -> !link.sourceNodeId().equals(nodeId) && !link.targetNodeId().equals(nodeId))
                .toList();
        return new TraceDocument(
                3,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                document.nodes(),
                links,
                document.expandedNodeIds());
    }

    public TraceDocument setParent(TraceDocument document, String nodeId, String newParentId, Instant now) {
        validateParentChange(document, nodeId, newParentId);
        List<TraceNode> updated = new ArrayList<>(document.nodes());
        for (int i = 0; i < updated.size(); i++) {
            if (updated.get(i).id().equals(nodeId)) {
                TraceNode node = updated.get(i);
                updated.set(i, new TraceNode(
                        node.id(), node.displayName(), node.qualifiedName(), node.signature(),
                        node.filePath(), node.line(), node.language(), node.note(),
                        node.navigationHint(), newParentId, node.title()));
                break;
            }
        }
        return new TraceDocument(
                3,
                document.id(), document.name(), document.description(),
                document.createdAt(), now,
                List.copyOf(updated), document.links(), document.expandedNodeIds());
    }

    public TraceDocument setParentAndIndex(TraceDocument document, String nodeId,
                                           String newParentId, int targetIndex, Instant now) {
        validateParentChange(document, nodeId, newParentId);
        List<TraceNode> nodes = new ArrayList<>(document.nodes());
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).id().equals(nodeId)) {
                TraceNode node = nodes.get(i);
                String oldParentId = node.parentId();

                // Compute the source node's sibling index before removal, so we can
                // adjust targetIndex when the caller's index is based on the pre-removal list.
                int sourceSiblingIdx = -1;
                if (Objects.equals(oldParentId, newParentId)) {
                    sourceSiblingIdx = 0;
                    for (int k = 0; k < i; k++) {
                        if (Objects.equals(nodes.get(k).parentId(), oldParentId)) {
                            sourceSiblingIdx++;
                        }
                    }
                }

                TraceNode updated = new TraceNode(
                        node.id(), node.displayName(), node.qualifiedName(), node.signature(),
                        node.filePath(), node.line(), node.language(), node.note(),
                        node.navigationHint(), newParentId, node.title());
                nodes.remove(i);

                if (targetIndex == -1) {
                    // Insert after the parent node and all its existing children
                    // (whichever comes last in the flat list).
                    int insertIdx = nodes.size();
                    for (int j = nodes.size() - 1; j >= 0; j--) {
                        TraceNode n = nodes.get(j);
                        if (Objects.equals(n.parentId(), newParentId) || n.id().equals(newParentId)) {
                            insertIdx = j + 1;
                            break;
                        }
                    }
                    nodes.add(insertIdx, updated);
                } else {
                    // Adjust targetIndex if the source was a sibling that preceded the
                    // target in the flat list — removal shifts sibling indices down by 1.
                    int adjusted = targetIndex;
                    if (sourceSiblingIdx >= 0 && sourceSiblingIdx < targetIndex) {
                        adjusted--;
                    }
                    // Find the adjusted-th sibling and insert before it.
                    int insertIdx = nodes.size(); // default: append at end
                    int siblingsSeen = 0;
                    for (int j = 0; j < nodes.size(); j++) {
                        if (Objects.equals(nodes.get(j).parentId(), newParentId)) {
                            if (siblingsSeen == adjusted) {
                                insertIdx = j;
                                break;
                            }
                            siblingsSeen++;
                        }
                    }
                    nodes.add(insertIdx, updated);
                }
                break;
            }
        }
        return new TraceDocument(
                3,
                document.id(), document.name(), document.description(),
                document.createdAt(), now,
                List.copyOf(nodes), document.links(), document.expandedNodeIds());
    }

    public TraceDocument setExpandedNodeIds(TraceDocument document, Set<String> expandedNodeIds, Instant now) {
        return new TraceDocument(
                3,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                document.nodes(),
                document.links(),
                expandedNodeIds);
    }

    /**
     * 切换节点的展开状态
     */
    public TraceDocument toggleExpandedNode(TraceDocument document, String nodeId, boolean expand, Instant now) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(now, "now");

        Set<String> expandedNodeIds = new java.util.HashSet<>(document.expandedNodeIds());

        if (expand) {
            expandedNodeIds.add(nodeId);
        } else {
            expandedNodeIds.remove(nodeId);
        }

        return new TraceDocument(
                3,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                document.nodes(),
                document.links(),
                expandedNodeIds);
    }

    /**
     * 展开所有有子节点的节点
     */
    public TraceDocument expandAllNodes(TraceDocument document, Instant now) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(now, "now");

        List<TraceNode> nodes = document.nodes();

        // 预构建 parentId 集合，O(n) 复杂度
        Set<String> parentIds = nodes.stream()
                .map(TraceNode::parentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> expandedNodeIds = new HashSet<>();

        // 只展开有子节点的节点
        for (TraceNode node : nodes) {
            if (parentIds.contains(node.id())) {
                expandedNodeIds.add(node.id());
            }
        }

        return new TraceDocument(
                3,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                nodes,
                document.links(),
                expandedNodeIds);
    }

    /**
     * 折叠所有节点
     */
    public TraceDocument collapseAllNodes(TraceDocument document, Instant now) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(now, "now");

        return new TraceDocument(
                3,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                document.nodes(),
                document.links(),
                Set.of());
    }

    private static void validateParentChange(TraceDocument document, String nodeId, String newParentId) {
        if (newParentId == null) {
            return; // moving to root is always valid
        }
        // A node cannot be its own parent.
        if (nodeId.equals(newParentId)) {
            throw new IllegalArgumentException("A node cannot be its own parent");
        }
        // Walk up the parent chain from newParentId; if we reach nodeId, it's a cycle.
        String parentId = newParentId;
        java.util.Set<String> visited = new java.util.HashSet<>();
        while (parentId != null) {
            if (!visited.add(parentId)) {
                throw new IllegalArgumentException("Circular parent reference detected");
            }
            if (parentId.equals(nodeId)) {
                throw new IllegalArgumentException(
                        "Cannot move a node under its own descendant — circular reference");
            }
            String currentId = parentId;
            TraceNode parent = document.nodes().stream()
                    .filter(n -> n.id().equals(currentId))
                    .findFirst()
                    .orElse(null);
            parentId = parent == null ? null : parent.parentId();
        }
    }
}
