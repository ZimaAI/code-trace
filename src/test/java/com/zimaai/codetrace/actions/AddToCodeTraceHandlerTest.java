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
import java.time.Instant;
import java.util.List;
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

    @Test
    void prefersSourceNodeAfterAddingSourceAndDetectedTarget() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.createNewFile("trace-2.json", "Trace 2");

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

        AddToCodeTraceHandler handler = new AddToCodeTraceHandler(
                controller,
                new FakeCaptureService(source, Optional.of(target)),
                new RecordingPrompts(true),
                () -> {
                });

        handler.handle(null, null, null);

        String sourceId = controller.state().currentDocument().nodes().get(0).id();
        assertEquals(sourceId, controller.state().preferredSelectedNodeId());
    }

    @Test
    void prefersReusedSourceNodeWhenSameSourceAlreadyExists() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.createNewFile("trace-3.json", "Trace 3");

        TraceNode existing = new TraceNode(
                "node-existing",
                "return authService.login(user);",
                "AuthController#login",
                "login(User user)",
                "src/AuthController.java",
                21,
                "JAVA",
                "",
                "AuthController#login(User)");
        controller.addNode(existing);

        AddToCodeTraceHandler handler = new AddToCodeTraceHandler(
                controller,
                new FakeCaptureService(existing, Optional.empty()),
                new RecordingPrompts(true),
                () -> {
                });

        handler.handle(null, null, null);

        assertEquals("node-existing", controller.state().preferredSelectedNodeId());
    }

    @Test
    void insertsSourceAfterFocusedNodeAndPrefersItAfterRefresh() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-2.json", documentWithThreeNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("trace-2.json");
        controller.setFocusedNodeId("node-2");

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

        AddToCodeTraceHandler handler = new AddToCodeTraceHandler(
                controller,
                new FakeCaptureService(source, Optional.empty()),
                new RecordingPrompts(false),
                () -> {
                });

        handler.handle(null, null, null);

        String insertedId = controller.state().preferredSelectedNodeId();
        assertEquals(List.of("node-1", "node-2", insertedId, "node-3"),
                controller.state().currentDocument().nodes().stream().map(TraceNode::id).toList());
    }

    @Test
    void appendsSourceToBottomWhenNothingIsFocused() {
        TraceStorageService storage = new TraceStorageService(tempDir, new TraceJsonMapper());
        storage.save("trace-3.json", documentWithThreeNodes());
        CodeTraceController controller = new CodeTraceController(storage, node -> true);
        controller.load("trace-3.json");

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

        AddToCodeTraceHandler handler = new AddToCodeTraceHandler(
                controller,
                new FakeCaptureService(source, Optional.empty()),
                new RecordingPrompts(false),
                () -> {
                });

        handler.handle(null, null, null);

        assertEquals(4, controller.state().currentDocument().nodes().size());
        assertEquals(controller.state().currentDocument().nodes().get(3).id(),
                controller.state().preferredSelectedNodeId());
    }

    private static TraceDocument documentWithThreeNodes() {
        return new TraceDocument(
                2,
                "trace-test",
                "Trace Test",
                "",
                Instant.parse("2026-05-29T10:00:00Z"),
                Instant.parse("2026-05-29T10:00:00Z"),
                List.of(
                        new TraceNode("node-1", "line 1", "A#a", "a()", "A.java", 10, "JAVA", "", "A#a"),
                        new TraceNode("node-2", "line 2", "B#b", "b()", "B.java", 20, "JAVA", "", "B#b"),
                        new TraceNode("node-3", "line 3", "C#c", "c()", "C.java", 30, "JAVA", "", "C#c")),
                List.of());
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
