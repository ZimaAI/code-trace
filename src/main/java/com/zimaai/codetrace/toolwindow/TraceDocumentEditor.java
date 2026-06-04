package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import java.time.Instant;
import java.util.ArrayList;
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
            if (!nodeIds.contains(link.sourceNodeId()) || !nodeIds.contains(link.targetNodeId())) {
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
                TraceNode updated = new TraceNode(
                        node.id(), node.displayName(), node.qualifiedName(), node.signature(),
                        node.filePath(), node.line(), node.language(), node.note(),
                        node.navigationHint(), newParentId, node.title());
                nodes.remove(i);
                int insertIdx = 0;
                int siblingsSeen = 0;
                for (int j = 0; j < nodes.size(); j++) {
                    if (Objects.equals(nodes.get(j).parentId(), newParentId)) {
                        if (siblingsSeen == targetIndex) {
                            insertIdx = j;
                            break;
                        }
                        siblingsSeen++;
                    }
                }
                if (siblingsSeen <= targetIndex) {
                    insertIdx = nodes.size();
                }
                nodes.add(insertIdx, updated);
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
