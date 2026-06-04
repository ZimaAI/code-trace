package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Tests for drag-and-drop repositioning via setParentAndIndex.
 * Verifies that nodes end up at the correct position in the flat list.
 */
class TraceDocumentEditorDragTest {

    private static final Instant NOW = Instant.now();
    private final TraceDocumentEditor editor = new TraceDocumentEditor();

    private static TraceNode root(String id) {
        return new TraceNode(id, id, "", "", "", 0, "", "", "", null, null);
    }

    private static TraceNode child(String id, String parentId) {
        return new TraceNode(id, id, "", "", "", 0, "", "", "", parentId, null);
    }

    private static TraceDocument doc(TraceNode... nodes) {
        return new TraceDocument(3, "doc-1", "test", "", NOW, NOW,
                List.of(nodes), List.of(), Set.of());
    }

    private static List<String> nodeIds(TraceDocument doc) {
        return doc.nodes().stream().map(TraceNode::id).collect(Collectors.toList());
    }

    private static List<String> parentIds(TraceDocument doc) {
        return doc.nodes().stream().map(TraceNode::parentId).collect(Collectors.toList());
    }

    // === 同父节点内重排序 ===

    @Test
    void moveRootNodeForward_insertBeforeTarget() {
        // A B C D → insert A before D (sibling index 3) → B C A D
        TraceDocument d = doc(root("A"), root("B"), root("C"), root("D"));
        TraceDocument result = editor.setParentAndIndex(d, "A", null, 3, NOW);
        assertEquals(List.of("B", "C", "A", "D"), nodeIds(result));
    }

    @Test
    void moveRootNodeForward_insertAfterTarget() {
        // A B C D → insert A after C (sibling index 3) → B C A D
        // (getSiblingIndex(C)=2, lower half → +1 → 3)
        TraceDocument d = doc(root("A"), root("B"), root("C"), root("D"));
        TraceDocument result = editor.setParentAndIndex(d, "A", null, 3, NOW);
        assertEquals(List.of("B", "C", "A", "D"), nodeIds(result));
    }

    @Test
    void moveRootNodeForward_insertBeforeB() {
        // A B C D → insert A before B (sibling index 1) → A B C D
        // (A stays near the front, but now after removal and reinsertion)
        TraceDocument d = doc(root("A"), root("B"), root("C"), root("D"));
        TraceDocument result = editor.setParentAndIndex(d, "A", null, 1, NOW);
        assertEquals(List.of("A", "B", "C", "D"), nodeIds(result));
    }

    @Test
    void moveRootNodeBackward() {
        // A B C D → insert D before B (sibling index 1) → A D B C
        TraceDocument d = doc(root("A"), root("B"), root("C"), root("D"));
        TraceDocument result = editor.setParentAndIndex(d, "D", null, 1, NOW);
        assertEquals(List.of("A", "D", "B", "C"), nodeIds(result));
    }

    @Test
    void moveRootNodeToFirstPosition() {
        // A B C → insert C before A (sibling index 0) → C A B
        TraceDocument d = doc(root("A"), root("B"), root("C"));
        TraceDocument result = editor.setParentAndIndex(d, "C", null, 0, NOW);
        assertEquals(List.of("C", "A", "B"), nodeIds(result));
    }

    @Test
    void moveNodeBetweenTwoRootNodes() {
        // A B C D → insert B before D (sibling index 3) → A C B D
        TraceDocument d = doc(root("A"), root("B"), root("C"), root("D"));
        TraceDocument result = editor.setParentAndIndex(d, "B", null, 3, NOW);
        assertEquals(List.of("A", "C", "B", "D"), nodeIds(result));
    }

    @Test
    void moveRootNodeToLastSiblingPosition() {
        // A B C D → insert A after D (sibling index 4, beyond range) → B C D A
        TraceDocument d = doc(root("A"), root("B"), root("C"), root("D"));
        TraceDocument result = editor.setParentAndIndex(d, "A", null, 4, NOW);
        assertEquals(List.of("B", "C", "D", "A"), nodeIds(result));
    }

    // === 拖拽成为子节点（targetIndex = -1） ===

    @Test
    void reparentAsLastChild_noExistingChildren() {
        // A B C → drag A to become last child of B → B(+A) C
        TraceDocument d = doc(root("A"), root("B"), root("C"));
        TraceDocument result = editor.setParentAndIndex(d, "A", "B", -1, NOW);
        assertEquals(List.of("B", "A", "C"), nodeIds(result));
        assertEquals("B", parentIds(result).get(1));
    }

    @Test
    void reparentAsLastChild_withExistingChildren() {
        // A B Cx D → drag A to become last child of C → B Cx C(+A) D
        TraceDocument d = doc(root("A"), root("B"), child("Cx", "C"), root("C"), root("D"));
        TraceDocument result = editor.setParentAndIndex(d, "A", "C", -1, NOW);
        assertEquals(List.of("B", "Cx", "C", "A", "D"), nodeIds(result));
        assertEquals("C", parentIds(result).get(3));
    }

    @Test
    void reparentChildToAnotherParent() {
        // Ax B Cx C → drag Ax to become last child of C → B Cx C(+Ax)
        TraceDocument d = doc(child("Ax", "A"), root("B"), child("Cx", "C"), root("C"));
        TraceDocument result = editor.setParentAndIndex(d, "Ax", "C", -1, NOW);
        assertEquals(List.of("B", "Cx", "C", "Ax"), nodeIds(result));
        assertEquals("C", parentIds(result).get(3));
    }
}
