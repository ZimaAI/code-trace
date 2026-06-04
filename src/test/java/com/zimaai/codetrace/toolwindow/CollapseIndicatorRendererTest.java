package com.zimaai.codetrace.toolwindow;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CollapseIndicatorRendererTest {

    @Test
    void testBuildTreePrefix_RootNode_ReturnsEmptyString() {
        String prefix = CollapseIndicatorRenderer.buildTreePrefix(0, true, true, true);

        // 根节点返回空字符串，图标由渲染器单独设置
        assertEquals("", prefix);
    }

    @Test
    void testBuildTreePrefix_ChildNode_ReturnsTreeLine() {
        String prefix = CollapseIndicatorRenderer.buildTreePrefix(1, false, false, false);

        // 中间子节点返回 tee line
        assertTrue(prefix.contains("├──"));
    }

    @Test
    void testBuildTreePrefix_LastChild_ReturnsCornerLine() {
        String prefix = CollapseIndicatorRenderer.buildTreePrefix(1, false, true, true);

        // 最后一个子节点返回 corner line
        assertTrue(prefix.contains("└──"));
    }

    @Test
    void testBuildTreePrefix_MiddleChild_ReturnsTeeLine() {
        String prefix = CollapseIndicatorRenderer.buildTreePrefix(1, false, false, false);

        // 中间子节点返回 tee line
        assertTrue(prefix.contains("├──"));
    }
}
