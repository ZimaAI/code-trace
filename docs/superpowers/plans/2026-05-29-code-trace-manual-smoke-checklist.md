# Code-Trace Single-Version Node Linking Smoke Checklist

## Startup

1. Run in project root: `.\gradlew.bat runIde`
2. Wait for Sandbox IDEA to launch.
3. Open any source project in Sandbox IDEA.

## Tool Window Basics

1. `code-trace` is visible in the left tool window bar.
2. Open it and confirm visible controls:
   - Top buttons: `Refresh / Save Trace Note / Save Node Note / Set as Source / Link To Here / Unlink`
   - Left JSON file list
   - Right trace note, node list, node note
3. Confirm removed controls are not visible:
   - `Start Recording`
   - `Stop Recording`
   - `Save`
   - `Add Node`
   - `History`

## File Management

1. `New`, `Rename`, `Copy`, `Delete`, `Refresh` all work from the left pane.
2. Refresh keeps current file selected.
3. If a v1 JSON is modified externally, `Refresh` still loads nodes through migration.

## Note Save

1. Edit trace note and verify `Save Trace Note` becomes enabled.
2. Click save, reopen file, and confirm trace note is persisted.
3. Select a node, edit node note, and verify `Save Node Note` becomes enabled.
4. Click save, refresh file, and confirm node note is persisted.

## Nodes And Links

1. Every node row text is exactly one line of code (`displayName`).
2. Select one node and click `Set as Source`; the same node remains selected and link status shows source id.
3. Select another node and click `Link To Here`; source/target styling appears and the target node remains selected.
4. Click `Unlink`; linked styling is removed and the current node remains selected.
5. Use `Move Up` or `Move Down` on a selected node and confirm the same node remains selected after the list refreshes.
6. Delete the currently selected node and confirm the node list ends with no selection.

## Editor Popup Action

1. Right-click in an in-project editor file and find `Add to code-trace`.
2. Ensure a JSON is selected in Tool Window before triggering the action.
3. Trigger `Add to code-trace` and confirm the source node becomes the selected node in the Tool Window.
4. If the same source line already exists, trigger the action again and confirm the existing source node becomes selected instead of keeping the previous selection.
5. If target confirmation appears and you choose `Yes`, target node and `DETECTED` link are created, but the selected node remains the source node.
6. Trigger the action from a file outside the current project root and confirm a rejection message appears and the trace content does not change.

## Navigation

1. Double-click a node whose `filePath` is relative and verify editor navigates to the correct file and line.
2. Edit one node JSON manually so `filePath` becomes an absolute path, refresh the Tool Window, and verify double-click navigation still works.
3. If code moved externally, failure is user-visible and tool window remains stable.

## Regression

Run: `.\gradlew.bat test`

Expected: `BUILD SUCCESSFUL`
