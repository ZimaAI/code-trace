package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import javax.swing.JTree;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeTracePanelTreeTest {
    @TempDir
    Path tempDir;

    @Test
    void treeModelReflectsNestedNodesAfterReload() throws Exception {
        TraceDocument doc = new TraceDocument(
                3, "t1", "T1", "", Instant.now(), Instant.now(),
                List.of(
                        new TraceNode("n1", "root", "", "", "", 0, "", "", ""),
                        new TraceNode("n2", "child", "", "", "", 0, "", "", "", "n1", null)),
                List.of(), Set.of());

        CodeTracePanel panel = panelFor(doc);
        JTree tree = tree(panel);
        TraceTreeModel model = (TraceTreeModel) tree.getModel();

        Object root = model.getRoot();
        assertEquals(1, model.getChildCount(root)); // only n1 is top-level
    }

    @Test
    void treeSelectionSyncsFocusedNodeId() throws Exception {
        TraceDocument doc = new TraceDocument(
                3, "t1", "T1", "", Instant.now(), Instant.now(),
                List.of(new TraceNode("n1", "node", "", "", "", 0, "", "", "")),
                List.of(), Set.of());

        CodeTracePanel panel = panelFor(doc);
        CodeTraceController controller = controller(panel);
        JTree tree = tree(panel);

        TreePath path = tree.getPathForRow(0);
        tree.setSelectionPath(path);
        assertEquals("n1", controller.focusedNodeId());
    }

    @Test
    void usesLinkedNodeTreeCellRenderer() throws Exception {
        TraceDocument doc = new TraceDocument(
                3, "t1", "T1", "", Instant.now(), Instant.now(),
                List.of(new TraceNode("n1", "node", "", "", "", 0, "", "", "")),
                List.of(), Set.of());

        CodeTracePanel panel = panelFor(doc);
        JTree tree = tree(panel);

        assertNotNull(tree.getCellRenderer());
        assertTrue(tree.getCellRenderer() instanceof LinkedNodeTreeCellRenderer);
    }

    @Test
    void installsTreeDragAndDropSupport() throws Exception {
        TraceDocument doc = new TraceDocument(
                3, "t1", "T1", "", Instant.now(), Instant.now(),
                List.of(new TraceNode("n1", "node", "", "", "", 0, "", "", "")),
                List.of(), Set.of());

        CodeTracePanel panel = panelFor(doc);
        JTree tree = tree(panel);

        assertTrue(tree.getTransferHandler() instanceof NodeTreeTransferHandler);
    }

    @Test
    void dragReparentSetsParentId() throws Exception {
        TraceDocument doc = new TraceDocument(
                3, "t1", "T1", "", Instant.now(), Instant.now(),
                List.of(
                        new TraceNode("n1", "parent", "", "", "", 0, "", "", ""),
                        new TraceNode("n2", "child", "", "", "", 0, "", "", "", "n1", null)),
                List.of(), Set.of());

        CodeTracePanel panel = panelFor(doc);
        CodeTraceController controller = controller(panel);

        // Move child out: set n2 parentId to null
        controller.setParent("n2", null);

        TraceNode n2 = controller.state().currentDocument().nodes().stream()
                .filter(n -> n.id().equals("n2")).findFirst().orElseThrow();
        assertEquals(null, n2.parentId());
    }

    @Test
    void editDialogIncludesTitleField() throws Exception {
        TraceDocument doc = new TraceDocument(
                3, "t1", "T1", "", Instant.now(), Instant.now(),
                List.of(new TraceNode("n1", "disp", "", "", "", 0, "", "", "", null, null)),
                List.of(), Set.of());

        CodeTracePanel panel = panelFor(doc);
        CodeTraceController controller = controller(panel);

        TraceNode updated = new TraceNode("n1", "disp", "", "", "", 0, "", "", "", null, "新标题");
        controller.updateNode(updated);

        TraceNode reloaded = controller.state().currentDocument().nodes().get(0);
        assertEquals("新标题", reloaded.title());
    }

    private CodeTracePanel panelFor(TraceDocument document) {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", document);
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        CodeTracePanel panel = new CodeTracePanel(controller);
        panel.reloadFromDisk();
        return panel;
    }

    private static JTree tree(CodeTracePanel panel) throws Exception {
        Field editorPanelField = CodeTracePanel.class.getDeclaredField("editorPanel");
        editorPanelField.setAccessible(true);
        TraceEditorPanel editorPanel = (TraceEditorPanel) editorPanelField.get(panel);
        return editorPanel.nodeTree();
    }

    private static CodeTraceController controller(CodeTracePanel panel) throws Exception {
        Field field = CodeTracePanel.class.getDeclaredField("controller");
        field.setAccessible(true);
        return (CodeTraceController) field.get(panel);
    }
}
