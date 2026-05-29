package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLink;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CodeTracePanelNavigationTest {
    @TempDir
    Path tempDir;

    @Test
    void disablesEndpointNavigationForUnlinkedSelection() {
        AtomicReference<TraceNode> navigated = new AtomicReference<>();
        CodeTracePanel panel = panelFor(documentWithLinkedAndUnlinkedNodes(), navigated);

        panel.editorPanel().nodeList().setSelectedIndex(2);

        assertFalse(panel.editorPanel().goToSourceButton().isEnabled());
        assertFalse(panel.editorPanel().goToTargetButton().isEnabled());
    }

    @Test
    void navigatesToBothLinkEndpointsFromEitherSide() {
        AtomicReference<TraceNode> navigated = new AtomicReference<>();
        CodeTracePanel panel = panelFor(documentWithLinkedAndUnlinkedNodes(), navigated);

        panel.editorPanel().nodeList().setSelectedIndex(0);
        assertTrue(panel.editorPanel().goToSourceButton().isEnabled());
        assertTrue(panel.editorPanel().goToTargetButton().isEnabled());
        panel.editorPanel().goToSourceButton().doClick();
        assertEquals("node-1", navigated.get().id());
        panel.editorPanel().goToTargetButton().doClick();
        assertEquals("node-2", navigated.get().id());

        panel.editorPanel().nodeList().setSelectedIndex(1);
        panel.editorPanel().goToSourceButton().doClick();
        assertEquals("node-1", navigated.get().id());
        panel.editorPanel().goToTargetButton().doClick();
        assertEquals("node-2", navigated.get().id());
    }

    @Test
    void disablesMissingEndpointAndClearsButtonsAfterUnlinkOrDelete() {
        AtomicReference<TraceNode> navigated = new AtomicReference<>();
        CodeTracePanel missingTargetPanel = panelFor(documentWithMissingTarget(), navigated);

        missingTargetPanel.editorPanel().nodeList().setSelectedIndex(0);
        assertTrue(missingTargetPanel.editorPanel().goToSourceButton().isEnabled());
        assertFalse(missingTargetPanel.editorPanel().goToTargetButton().isEnabled());

        CodeTracePanel unlinkPanel = panelFor(documentWithLinkedAndUnlinkedNodes(), new AtomicReference<>());
        unlinkPanel.editorPanel().nodeList().setSelectedIndex(0);
        unlinkPanel.editorPanel().unlinkButton().doClick();
        assertFalse(unlinkPanel.editorPanel().goToSourceButton().isEnabled());
        assertFalse(unlinkPanel.editorPanel().goToTargetButton().isEnabled());

        CodeTracePanel deletePanel = panelFor(documentWithLinkedAndUnlinkedNodes(), new AtomicReference<>());
        deletePanel.editorPanel().nodeList().setSelectedIndex(0);
        deletePanel.editorPanel().deleteNodeButton().doClick();
        assertTrue(deletePanel.editorPanel().nodeList().isSelectionEmpty());
        assertFalse(deletePanel.editorPanel().goToSourceButton().isEnabled());
        assertFalse(deletePanel.editorPanel().goToTargetButton().isEnabled());
    }

    @Test
    void keepsDoubleClickNavigationForCurrentNode() {
        AtomicReference<TraceNode> navigated = new AtomicReference<>();
        CodeTracePanel panel = panelFor(documentWithLinkedAndUnlinkedNodes(), navigated);

        panel.editorPanel().nodeList().setSelectedIndex(1);

        MouseEvent event = new MouseEvent(
                panel.editorPanel().nodeList(),
                MouseEvent.MOUSE_CLICKED,
                System.currentTimeMillis(),
                0,
                10,
                10,
                2,
                false);
        for (MouseListener listener : panel.editorPanel().nodeList().getMouseListeners()) {
            listener.mouseClicked(event);
        }

        assertEquals("node-2", navigated.get().id());
    }

    private CodeTracePanel panelFor(TraceDocument document, AtomicReference<TraceNode> navigated) {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-1.json", document);
        CodeTraceController controller = new CodeTraceController(storage, node -> {
            navigated.set(node);
            return true;
        });
        CodeTracePanel panel = new CodeTracePanel(controller);
        panel.reloadFromDisk();
        return panel;
    }

    private static TraceDocument documentWithLinkedAndUnlinkedNodes() {
        return new TraceDocument(
                2,
                "trace-1",
                "Trace 1",
                "",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(
                        new TraceNode("node-1", "source line", "A#a", "a()", "A.java", 10, "JAVA", "", "A#a"),
                        new TraceNode("node-2", "target line", "B#b", "b()", "B.java", 20, "JAVA", "", "B#b"),
                        new TraceNode("node-3", "standalone line", "C#c", "c()", "C.java", 30, "JAVA", "", "C#c")),
                List.of(new TraceLink("link-1", "node-1", "node-2", Instant.parse("2026-05-29T10:01:00Z"), TraceLinkKind.MANUAL)));
    }

    private static TraceDocument documentWithMissingTarget() {
        return new TraceDocument(
                2,
                "trace-2",
                "Trace 2",
                "",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(new TraceNode("node-1", "source line", "A#a", "a()", "A.java", 10, "JAVA", "", "A#a")),
                List.of(new TraceLink("link-1", "node-1", "node-missing", Instant.parse("2026-05-29T10:01:00Z"), TraceLinkKind.MANUAL)));
    }
}
