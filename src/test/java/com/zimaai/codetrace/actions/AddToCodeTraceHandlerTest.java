package com.zimaai.codetrace.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceLinkKind;
import com.zimaai.codetrace.model.TraceNode;
import com.zimaai.codetrace.storage.TraceJsonMapper;
import com.zimaai.codetrace.storage.TraceStorageService;
import com.zimaai.codetrace.toolwindow.CodeTraceController;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AddToCodeTraceHandlerTest {
    @TempDir
    Path tempDir;

    @Test
    void appendsSourceNodeAndDetectedTargetWhenUserAcceptsLink() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.createNewFile("trace-1.json", "Trace 1");

        TraceNode source = new TraceNode(
                "ignored-source-id",
                "return authService.login(user);",
                "AuthController#login",
                "login(User user)",
                "src/AuthController.java",
                21,
                "JAVA",
                "",
                "AuthController#login(User)");
        TraceNode target = new TraceNode(
                "ignored-target-id",
                "public User login(User user) {",
                "AuthService#login",
                "login(User user)",
                "src/AuthService.java",
                14,
                "JAVA",
                "",
                "AuthService#login(User)");

        AtomicBoolean refreshed = new AtomicBoolean(false);
        RecordingPrompts prompts = new RecordingPrompts(true);
        AddToCodeTraceHandler handler = new AddToCodeTraceHandler(
                controller,
                new FakeCaptureService(source, Optional.of(target)),
                prompts,
                () -> refreshed.set(true));

        handler.handle(null, null, null);

        TraceDocument document = controller.state().currentDocument();
        assertEquals(2, document.nodes().size());
        assertEquals(1, document.links().size());
        assertEquals(TraceLinkKind.DETECTED, document.links().get(0).kind());
        assertTrue(refreshed.get());
        assertTrue(prompts.confirmCalled);
    }

    @Test
    void doesNotMutateTraceWhenSourceCaptureIsRejected() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.createNewFile("trace-1.json", "Trace 1");

        AtomicBoolean refreshed = new AtomicBoolean(false);
        RecordingPrompts prompts = new RecordingPrompts(true);
        AddToCodeTraceHandler handler = new AddToCodeTraceHandler(
                controller,
                new RejectingCaptureService("Source file is outside project root"),
                prompts,
                () -> refreshed.set(true));

        handler.handle(null, null, null);

        TraceDocument document = controller.state().currentDocument();
        assertTrue(document.nodes().isEmpty());
        assertTrue(document.links().isEmpty());
        assertFalse(refreshed.get());
        assertFalse(prompts.confirmCalled);
        assertEquals("Source file is outside project root", prompts.captureErrorMessage);
    }

    private static final class FakeCaptureService implements TraceNodeCaptureService {
        private final TraceNode source;
        private final Optional<TraceNode> target;

        private FakeCaptureService(TraceNode source, Optional<TraceNode> target) {
            this.source = source;
            this.target = target;
        }

        @Override
        public TraceNode captureCurrentLine(
                com.intellij.openapi.project.Project project,
                com.intellij.openapi.editor.Editor editor,
                com.intellij.psi.PsiFile psiFile) {
            return source;
        }

        @Override
        public Optional<TraceNode> detectTarget(
                com.intellij.openapi.project.Project project,
                com.intellij.openapi.editor.Editor editor,
                com.intellij.psi.PsiFile psiFile) {
            return target;
        }
    }

    private static final class RecordingPrompts implements TraceUserPrompts {
        private final boolean confirm;
        private boolean confirmCalled;
        private String captureErrorMessage;

        private RecordingPrompts(boolean confirm) {
            this.confirm = confirm;
        }

        @Override
        public void showSelectTraceMessage(com.intellij.openapi.project.Project project) {
        }

        @Override
        public boolean confirmDetectedLink(
                com.intellij.openapi.project.Project project,
                String sourceDisplayName,
                String targetDisplayName) {
            confirmCalled = true;
            return confirm;
        }

        @Override
        public void showLinkError(com.intellij.openapi.project.Project project, String message) {
        }

        @Override
        public void showCaptureError(com.intellij.openapi.project.Project project, String message) {
            captureErrorMessage = message;
        }
    }

    private static final class RejectingCaptureService implements TraceNodeCaptureService {
        private final String message;

        private RejectingCaptureService(String message) {
            this.message = message;
        }

        @Override
        public TraceNode captureCurrentLine(
                com.intellij.openapi.project.Project project,
                com.intellij.openapi.editor.Editor editor,
                com.intellij.psi.PsiFile psiFile) {
            throw new IllegalArgumentException(message);
        }

        @Override
        public Optional<TraceNode> detectTarget(
                com.intellij.openapi.project.Project project,
                com.intellij.openapi.editor.Editor editor,
                com.intellij.psi.PsiFile psiFile) {
            throw new AssertionError("detectTarget should not be called when capture fails");
        }
    }
}
