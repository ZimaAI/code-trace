package com.zimaai.codetrace.actions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.FakePsiElement;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PsiTraceNodeCaptureServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void captureCurrentLineStoresProjectRelativeSlashSeparatedSourcePath() {
        Path projectRoot = tempDir.resolve("project");
        Path sourceFile = projectRoot.resolve(Path.of("src", "main", "java", "demo", "App.java"));
        Document document = document("class App {\n    void run() {}\n}\n");
        MethodPsiElement callable = new MethodPsiElement(
                "App#run",
                "void run() {}",
                16,
                null,
                null,
                null);
        PsiFile psiFile = psiFile(document, sourceFile, callable);
        callable.setContainingFile(psiFile);
        Editor editor = editor(document, 20, 1);

        TraceNodeCaptureService service = new PsiTraceNodeCaptureService();
        var node = service.captureCurrentLine(projectWithBasePath(projectRoot.toString()), editor, psiFile);

        assertEquals("src/main/java/demo/App.java", node.filePath());
        assertEquals(2, node.line());
    }

    @Test
    void detectTargetReturnsEmptyWhenTargetFileIsOutsideProjectRoot() {
        Path projectRoot = tempDir.resolve("project");
        Path sourceFile = projectRoot.resolve(Path.of("src", "main", "java", "demo", "Caller.java"));
        Path externalTargetFile = tempDir.resolve(Path.of("external", "Dependency.java"));

        Document sourceDocument = document("class Caller {\n    void run() { helper(); }\n}\n");
        Document targetDocument = document("class Dependency {\n    void helper() {}\n}\n");

        MethodPsiElement target = new MethodPsiElement(
                "Dependency#helper",
                "void helper() {}",
                23,
                null,
                null,
                null);
        PsiFile targetPsiFile = psiFile(targetDocument, externalTargetFile, target);
        target.setContainingFile(targetPsiFile);

        MethodPsiElement source = new MethodPsiElement(
                "Caller#run",
                "void run() { helper(); }",
                15,
                target,
                null,
                null);
        PsiFile sourcePsiFile = psiFile(sourceDocument, sourceFile, source);
        source.setContainingFile(sourcePsiFile);
        Editor editor = editor(sourceDocument, 20, 1);

        TraceNodeCaptureService service = new PsiTraceNodeCaptureService();
        Optional<?> detected = service.detectTarget(projectWithBasePath(projectRoot.toString()), editor, sourcePsiFile);

        assertTrue(detected.isEmpty());
    }

    @Test
    void detectTargetDoesNotSwallowProjectBasePathErrors() {
        Path sourceFile = tempDir.resolve(Path.of("project", "src", "main", "java", "demo", "Caller.java"));
        Path targetFile = tempDir.resolve(Path.of("project", "src", "main", "java", "demo", "Target.java"));

        Document sourceDocument = document("class Caller {\n    void run() { helper(); }\n}\n");
        Document targetDocument = document("class Target {\n    void helper() {}\n}\n");

        MethodPsiElement target = new MethodPsiElement(
                "Target#helper",
                "void helper() {}",
                19,
                null,
                null,
                null);
        PsiFile targetPsiFile = psiFile(targetDocument, targetFile, target);
        target.setContainingFile(targetPsiFile);

        MethodPsiElement source = new MethodPsiElement(
                "Caller#run",
                "void run() { helper(); }",
                15,
                target,
                null,
                null);
        PsiFile sourcePsiFile = psiFile(sourceDocument, sourceFile, source);
        source.setContainingFile(sourcePsiFile);
        Editor editor = editor(sourceDocument, 20, 1);

        TraceNodeCaptureService service = new PsiTraceNodeCaptureService();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.detectTarget(projectWithBasePath(null), editor, sourcePsiFile));
        assertEquals("Project base path is unavailable", exception.getMessage());
    }

    private static Project projectWithBasePath(String basePath) {
        return (Project) Proxy.newProxyInstance(
                Project.class.getClassLoader(),
                new Class<?>[] {Project.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getBasePath" -> basePath;
                    case "toString" -> "Project(" + basePath + ")";
                    default -> unsupported(method);
                });
    }

    private static Editor editor(Document document, int offset, int zeroBasedLine) {
        CaretModel caretModel = (CaretModel) Proxy.newProxyInstance(
                CaretModel.class.getClassLoader(),
                new Class<?>[] {CaretModel.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getOffset" -> offset;
                    case "getLogicalPosition" -> new LogicalPosition(zeroBasedLine, 0);
                    default -> unsupported(method);
                });
        return (Editor) Proxy.newProxyInstance(
                Editor.class.getClassLoader(),
                new Class<?>[] {Editor.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getCaretModel" -> caretModel;
                    case "getDocument" -> document;
                    default -> unsupported(method);
                });
    }

    private static Document document(String text) {
        int[] lineStarts = lineStarts(text);
        return (Document) Proxy.newProxyInstance(
                Document.class.getClassLoader(),
                new Class<?>[] {Document.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getLineCount" -> lineStarts.length;
                    case "getLineStartOffset" -> lineStarts[(int) args[0]];
                    case "getLineEndOffset" -> lineEnd(text, lineStarts, (int) args[0]);
                    case "getLineNumber" -> lineNumber(text, (int) args[0]);
                    case "getText" -> text.substring(((TextRange) args[0]).getStartOffset(), ((TextRange) args[0]).getEndOffset());
                    default -> unsupported(method);
                });
    }

    private static PsiFile psiFile(Document document, Path filePath, PsiElement elementAtOffset) {
        VirtualFile virtualFile = new StubVirtualFile(filePath);
        FileViewProvider viewProvider = (FileViewProvider) Proxy.newProxyInstance(
                FileViewProvider.class.getClassLoader(),
                new Class<?>[] {FileViewProvider.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getDocument" -> document;
                    default -> unsupported(method);
                });
        return (PsiFile) Proxy.newProxyInstance(
                PsiFile.class.getClassLoader(),
                new Class<?>[] {PsiFile.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findElementAt" -> elementAtOffset;
                    case "getVirtualFile" -> virtualFile;
                    case "getViewProvider" -> viewProvider;
                    case "getLanguage" -> Language.ANY;
                    case "toString" -> filePath.toString();
                    default -> unsupported(method);
                });
    }

    private static Object unsupported(java.lang.reflect.Method method) {
        throw new UnsupportedOperationException("Unexpected call: " + method.getName());
    }

    private static int[] lineStarts(String text) {
        java.util.ArrayList<Integer> starts = new java.util.ArrayList<>();
        starts.add(0);
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n' && index + 1 < text.length()) {
                starts.add(index + 1);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int lineEnd(String text, int[] lineStarts, int line) {
        int start = lineStarts[line];
        int nextStart = line + 1 < lineStarts.length ? lineStarts[line + 1] : text.length();
        return nextStart > start && text.charAt(nextStart - 1) == '\n' ? nextStart - 1 : nextStart;
    }

    private static int lineNumber(String text, int offset) {
        int line = 0;
        for (int index = 0; index < Math.min(offset, text.length()); index++) {
            if (text.charAt(index) == '\n') {
                line++;
            }
        }
        return line;
    }

    private static final class MethodPsiElement extends FakePsiElement {
        private final String debugName;
        private final String text;
        private final int startOffset;
        private final PsiElement navigationElement;
        private final PsiElement parent;
        private PsiFile containingFile;

        private MethodPsiElement(
                String debugName,
                String text,
                int startOffset,
                PsiElement navigationElement,
                PsiElement parent,
                PsiFile containingFile) {
            this.debugName = debugName;
            this.text = text;
            this.startOffset = startOffset;
            this.navigationElement = navigationElement == null ? this : navigationElement;
            this.parent = parent;
            this.containingFile = containingFile;
        }

        private void setContainingFile(PsiFile containingFile) {
            this.containingFile = containingFile;
        }

        @Override
        public PsiElement getParent() {
            return parent;
        }

        @Override
        public PsiElement getNavigationElement() {
            return navigationElement;
        }

        @Override
        public PsiFile getContainingFile() {
            return containingFile;
        }

        @Override
        public TextRange getTextRange() {
            return TextRange.from(startOffset, text.length());
        }

        @Override
        public String getText() {
            return text;
        }

        @Override
        public String toString() {
            return debugName;
        }
    }

    private static final class StubVirtualFile extends com.intellij.testFramework.LightVirtualFile {
        private final String path;

        private StubVirtualFile(Path path) {
            super(path.getFileName().toString(), "");
            this.path = path.toString().replace('\\', '/');
        }

        @Override
        public String getPath() {
            return path;
        }
    }
}
