package com.zimaai.codetrace.toolwindow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.zimaai.codetrace.model.TraceDocument;
import com.zimaai.codetrace.model.TraceNode;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JTable;
import org.junit.jupiter.api.Test;

class MultiSelectTransferHandlerTest {

    @Test
    void createTransferable_shouldSerializeMultipleNodeIds() throws Exception {
        // Given
        TraceDocument doc = createDoc(List.of(
                new TraceNode("node-1", "n1", "", "", "", 0, "", "", ""),
                new TraceNode("node-2", "n2", "", "", "", 0, "", "", ""),
                new TraceNode("node-3", "n3", "", "", "", 0, "", "", "")));
        Map<String, String> numberMap = Map.of("node-1", "1", "node-2", "2", "node-3", "3");
        NodeTableModel sourceModel = new NodeTableModel(doc.nodes(), numberMap, doc.links());
        FilteredNodeTableModel model = new FilteredNodeTableModel(sourceModel, doc);
        JTable table = new JTable(model);
        table.setRowSelectionInterval(0, 0);
        table.addRowSelectionInterval(1, 1);
        table.addRowSelectionInterval(2, 2);

        MultiSelectTransferHandler handler = new MultiSelectTransferHandler(null, () -> {});

        // When
        Transferable transferable = handler.createTransferable(table);

        // Then
        assertNotNull(transferable);
        String data = (String) transferable.getTransferData(DataFlavor.stringFlavor);
        assertEquals("node-1,node-2,node-3", data);
    }

    @Test
    void createTransferable_shouldReturnNullWhenNoSelection() {
        // Given
        TraceDocument doc = createDoc(List.of(
                new TraceNode("node-1", "n1", "", "", "", 0, "", "", "")));
        Map<String, String> numberMap = Map.of("node-1", "1");
        NodeTableModel sourceModel = new NodeTableModel(doc.nodes(), numberMap, doc.links());
        FilteredNodeTableModel model = new FilteredNodeTableModel(sourceModel, doc);
        JTable table = new JTable(model);
        // No selection

        MultiSelectTransferHandler handler = new MultiSelectTransferHandler(null, () -> {});

        // When
        Transferable transferable = handler.createTransferable(table);

        // Then
        assertEquals(null, transferable);
    }

    @Test
    void createTransferable_shouldSerializeSingleNodeId() throws Exception {
        // Given
        TraceDocument doc = createDoc(List.of(
                new TraceNode("node-1", "n1", "", "", "", 0, "", "", "")));
        Map<String, String> numberMap = Map.of("node-1", "1");
        NodeTableModel sourceModel = new NodeTableModel(doc.nodes(), numberMap, doc.links());
        FilteredNodeTableModel model = new FilteredNodeTableModel(sourceModel, doc);
        JTable table = new JTable(model);
        table.setRowSelectionInterval(0, 0);

        MultiSelectTransferHandler handler = new MultiSelectTransferHandler(null, () -> {});

        // When
        Transferable transferable = handler.createTransferable(table);

        // Then
        assertNotNull(transferable);
        String data = (String) transferable.getTransferData(DataFlavor.stringFlavor);
        assertEquals("node-1", data);
    }

    @Test
    void getSourceActions_shouldReturnMove() {
        MultiSelectTransferHandler handler = new MultiSelectTransferHandler(null, () -> {});
        assertEquals(javax.swing.TransferHandler.MOVE, handler.getSourceActions(null));
    }

    private static TraceDocument createDoc(List<TraceNode> nodes) {
        return new TraceDocument(3, "t1", "T1", "", Instant.now(), Instant.now(),
                nodes, List.of(), Set.of());
    }
}
