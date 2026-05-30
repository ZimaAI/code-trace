package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void usesWrapLayoutForNodeToolbarAndDisablesJumpButtonByDefault() {
        TraceEditorPanel panel = new TraceEditorPanel();

        LayoutManager layout = panel.nodeToolbar().getLayout();
        assertEquals(WrapLayout.class, layout.getClass());

        JButton goToLinked = panel.goToLinkedButton();
        assertEquals("Go To Linked", goToLinked.getText());
        assertFalse(goToLinked.isEnabled());
    }

    @Test
    void setsIconsOnAllToolbarButtons() {
        TraceEditorPanel panel = new TraceEditorPanel();

        assertNotNull(panel.editNodeButton().getIcon(), "Edit Node button should have an icon");
        assertNotNull(panel.deleteNodeButton().getIcon(), "Delete Node button should have an icon");
        assertNotNull(panel.moveUpButton().getIcon(), "Move Up button should have an icon");
        assertNotNull(panel.moveDownButton().getIcon(), "Move Down button should have an icon");
        assertNotNull(panel.setAsSourceButton().getIcon(), "Set as Source button should have an icon");
        assertNotNull(panel.linkToHereButton().getIcon(), "Link To Here button should have an icon");
        assertNotNull(panel.unlinkButton().getIcon(), "Unlink button should have an icon");
        assertNotNull(panel.goToLinkedButton().getIcon(), "Go To Linked button should have an icon");
        assertNotNull(panel.saveTraceNoteButton().getIcon(), "Save Trace Note button should have an icon");
        assertNotNull(panel.saveNodeNoteButton().getIcon(), "Save Node Note button should have an icon");
    }

    @Test
    void setsTooltipsOnAllToolbarButtons() {
        TraceEditorPanel panel = new TraceEditorPanel();

        assertNotNull(panel.editNodeButton().getToolTipText(), "Edit Node button should have a tooltip");
        assertNotNull(panel.deleteNodeButton().getToolTipText(), "Delete Node button should have a tooltip");
        assertNotNull(panel.moveUpButton().getToolTipText(), "Move Up button should have a tooltip");
        assertNotNull(panel.moveDownButton().getToolTipText(), "Move Down button should have a tooltip");
        assertNotNull(panel.setAsSourceButton().getToolTipText(), "Set as Source button should have a tooltip");
        assertNotNull(panel.linkToHereButton().getToolTipText(), "Link To Here button should have a tooltip");
        assertNotNull(panel.unlinkButton().getToolTipText(), "Unlink button should have a tooltip");
        assertNotNull(panel.goToLinkedButton().getToolTipText(), "Go To Linked button should have a tooltip");
        assertNotNull(panel.saveTraceNoteButton().getToolTipText(), "Save Trace Note button should have a tooltip");
        assertNotNull(panel.saveNodeNoteButton().getToolTipText(), "Save Node Note button should have a tooltip");
    }
}
