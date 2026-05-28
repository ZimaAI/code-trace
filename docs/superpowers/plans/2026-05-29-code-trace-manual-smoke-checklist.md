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
2. Select one node and click `Set as Source`; link status shows source id.
3. Select another node and click `Link To Here`; source/target styling appears.
4. Click `Unlink`; linked styling is removed.
5. For linked nodes, `Move Up / Move Down / Delete` affects the pair.

## Editor Popup Action

1. Right-click in editor and find `Add to code-trace`.
2. Ensure a JSON is selected in Tool Window before triggering the action.
3. Current line is added as a node and matches editor line text.
4. If target confirmation appears and you choose `Yes`, target node and `DETECTED` link are created.

## Navigation

1. Double-click a node and verify editor navigates to file and line.
2. If code moved externally, failure is user-visible and tool window remains stable.

## Regression

Run: `.\gradlew.bat test`

Expected: `BUILD SUCCESSFUL`
