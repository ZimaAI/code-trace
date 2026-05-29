package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class CodeTracePanelTest {
    @Test
    void keepsOnlyRefreshActionInTopToolbar() {
        assertEquals(List.of("Refresh"), CodeTracePanel.topToolbarButtonLabels());
        assertNull(findTopToolbarLabel("Save Trace Note"));
        assertNull(findTopToolbarLabel("Save Node Note"));
        assertNull(findTopToolbarLabel("Set as Source"));
        assertNull(findTopToolbarLabel("Link To Here"));
        assertNull(findTopToolbarLabel("Unlink"));
    }

    @Test
    void keepsSaveButtonsInsideEditorPanel() {
        TraceEditorPanel editorPanel = new TraceEditorPanel();

        assertNotNull(editorPanel.saveTraceNoteButton());
        assertNotNull(editorPanel.saveNodeNoteButton());
    }

    private static String findTopToolbarLabel(String label) {
        return CodeTracePanel.topToolbarButtonLabels().stream()
                .filter(label::equals)
                .findFirst()
                .orElse(null);
    }
}
