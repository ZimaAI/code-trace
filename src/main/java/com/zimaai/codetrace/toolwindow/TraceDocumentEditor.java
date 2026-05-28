package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class TraceDocumentEditor {
    public TraceDocument saveDescription(TraceDocument document, String description, Instant now) {
        return new TraceDocument(
                2,
                document.id(),
                document.name(),
                description,
                document.createdAt(),
                now,
                document.nodes(),
                document.links());
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
                        node.navigationHint()));
            } else {
                updatedNodes.add(node);
            }
        }
        return new TraceDocument(
                2,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                List.copyOf(updatedNodes),
                document.links());
    }

    public TraceDocument addNode(TraceDocument document, TraceNode node, Instant now) {
        List<TraceNode> nodes = new ArrayList<>(document.nodes());
        nodes.add(node);
        return new TraceDocument(
                2,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                List.copyOf(nodes),
                document.links());
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
                2,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                List.copyOf(nodes),
                document.links());
    }

    public TraceDocument link(TraceDocument document, String sourceNodeId, String targetNodeId, TraceLinkKind kind, Instant now) {
        Objects.requireNonNull(sourceNodeId, "sourceNodeId");
        Objects.requireNonNull(targetNodeId, "targetNodeId");
        if (sourceNodeId.equals(targetNodeId)) {
            throw new IllegalArgumentException("Cannot link a node to itself");
        }
        for (TraceLink link : document.links()) {
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
                2,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                document.nodes(),
                List.copyOf(links));
    }

    public TraceDocument unlink(TraceDocument document, String nodeId, Instant now) {
        List<TraceLink> links = document.links().stream()
                .filter(link -> !link.sourceNodeId().equals(nodeId) && !link.targetNodeId().equals(nodeId))
                .toList();
        return new TraceDocument(
                2,
                document.id(),
                document.name(),
                document.description(),
                document.createdAt(),
                now,
                document.nodes(),
                links);
    }
}
