package com.zimaai.codetrace.actions;

import com.intellij.openapi.project.Project;
import javax.swing.JOptionPane;

public final class SwingTraceUserPrompts implements TraceUserPrompts {
    @Override
    public void showSelectTraceMessage(Project project) {
        JOptionPane.showMessageDialog(
                null,
                "Select a code-trace JSON file in the Tool Window first.",
                "code-trace",
                JOptionPane.INFORMATION_MESSAGE);
    }

    @Override
    public boolean confirmDetectedLink(Project project, String sourceDisplayName, String targetDisplayName) {
        int choice = JOptionPane.showConfirmDialog(
                null,
                "Also add target and create a trace link?\n\nSource: \"" + sourceDisplayName + "\"\nTarget: \"" + targetDisplayName + "\"",
                "code-trace — Detected target",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    @Override
    public void showLinkError(Project project, String message) {
        JOptionPane.showMessageDialog(
                null,
                message,
                "code-trace — Link error",
                JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showCaptureError(Project project, String message) {
        JOptionPane.showMessageDialog(
                null,
                message,
                "code-trace — Capture error",
                JOptionPane.ERROR_MESSAGE);
    }
}
