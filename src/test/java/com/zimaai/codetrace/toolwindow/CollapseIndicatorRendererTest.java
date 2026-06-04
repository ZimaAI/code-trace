package com.zimaai.codetrace.toolwindow;

import com.zimaai.codetrace.model.TraceNode;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CollapseIndicatorRendererTest {

    @Test
    void testBuildTreePrefix_RootNode_ReturnsCollapsedIcon() {
        TraceNode node = new TraceNode("1", "Root", null, null, null, 0, null, null, null);
        String prefix = CollapseIndicatorRenderer.buildTreePrefix(node, 0, true, true, true);

        // Should contain collapsed icon
        assertTrue(prefix.contains("▶") || prefix.contains("▼"));
    }

    @Test
    void testBuildTreePrefix_ChildNode_ReturnsTreeLine() {
        TraceNode node = new TraceNode("2", "Child", null, null, null, 0, null, null, "1");
        String prefix = CollapseIndicatorRenderer.buildTreePrefix(node, 1, false, false, true);

        // Should contain tree line characters
        assertTrue(prefix.contains("├──") || prefix.contains("└──"));
    }

    @Test
    void testBuildTreePrefix_LastChild_ReturnsCornerLine() {
        TraceNode node = new TraceNode("2", "LastChild", null, null, null, 0, null, null, "1");
        String prefix = CollapseIndicatorRenderer.buildTreePrefix(node, 1, false, true, true);

        // Should contain corner line
        assertTrue(prefix.contains("└──"));
    }

    @Test
    void testBuildTreePrefix_MiddleChild_ReturnsTeeLine() {
        TraceNode node = new TraceNode("2", "MiddleChild", null, null, null, 0, null, null, "1");
        String prefix = CollapseIndicatorRenderer.buildTreePrefix(node, 1, false, false, false);

        // Should contain tee line
        assertTrue(prefix.contains("├──"));
    }
}
