package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.intellij.ui.JBColor;
import org.junit.jupiter.api.Test;

class TraceEditorPanelTest {
    @Test
    void enablesSoftWrapForNodeNoteEditing() {
        TraceEditorPanel panel = new TraceEditorPanel();

        assertTrue(panel.nodeNote().getLineWrap());
        assertTrue(panel.nodeNote().getWrapStyleWord());
    }

    @Test
    void appliesThemeAwareSelectionColorsForNodeNote() {
        TraceEditorPanel panel = new TraceEditorPanel();

        assertEquals(JBColor.class, panel.nodeNote().getSelectionColor().getClass());
        assertEquals(JBColor.class, panel.nodeNote().getSelectedTextColor().getClass());
        assertEquals(JBColor.class, panel.nodeNote().getCaretColor().getClass());
    }
}
