package com.zimaai.codetrace.actions;

import com.intellij.openapi.project.Project;
import javax.swing.JOptionPane;

public final class SwingTraceUserPrompts implements TraceUserPrompts {
    @Override
    public void showSelectTraceMessage(Project project) {
        JOptionPane.showMessageDialog(null, "Select a code-trace JSON in the Tool Window first.");
    }

    @Override
    public boolean confirmDetectedLink(Project project, String sourceDisplayName, String targetDisplayName) {
        int choice = JOptionPane.showConfirmDialog(
                null,
                "Also add target and create link?\nSource: " + sourceDisplayName + "\nTarget: " + targetDisplayName,
                "Detected target",
                JOptionPane.YES_NO_OPTION);
        return choice == JOptionPane.YES_OPTION;
    }

    @Override
    public void showLinkError(Project project, String message) {
        JOptionPane.showMessageDialog(null, message);
    }

    @Override
    public void showCaptureError(Project project, String message) {
        JOptionPane.showMessageDialog(null, message);
    }
}
