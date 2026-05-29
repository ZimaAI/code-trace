package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.intellij.ui.JBColor;
import java.awt.LayoutManager;
import javax.swing.JButton;
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

    @Test
    void usesWrapLayoutForNodeToolbarAndDisablesJumpButtonsByDefault() {
        TraceEditorPanel panel = new TraceEditorPanel();

        LayoutManager layout = panel.nodeToolbar().getLayout();
        assertEquals(WrapLayout.class, layout.getClass());

        JButton goToSource = panel.goToSourceButton();
        JButton goToTarget = panel.goToTargetButton();
        assertEquals("Go to Source", goToSource.getText());
        assertEquals("Go to Target", goToTarget.getText());
        assertFalse(goToSource.isEnabled());
        assertFalse(goToTarget.isEnabled());
    }
}
