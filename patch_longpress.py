import io, sys

PATH = r"D:\Project\StockChat\shared\src\commonMain\kotlin\com\kuikly\stockchat\page\ChatPage.kt"

with io.open(PATH, "r", encoding="utf-8", newline="") as f:
    lines = f.readlines()

# locate anchor: the "val recognized" line
idx = None
for i, ln in enumerate(lines):
    if "val recognized = state == \"start\" || state == \"end\"" in ln:
        idx = i
        break

if idx is None:
    print("ERROR: anchor line not found")
    sys.exit(1)

# find the closing "    }" of this function (first line that is exactly "    }" after idx)
end = None
for j in range(idx, len(lines)):
    if lines[j].rstrip("\r\n") == "    }":
        end = j
        break

if end is None:
    print("ERROR: function end not found")
    sys.exit(1)

print("anchor at line %d (1-based %d), end at %d" % (idx, idx + 1, end))

NEW_BODY = '''        if (state == "move") { println("[STOCKCHAT_DBG] handleStockEntityLongPress ignored(state=move)"); return }
        if (pendingEntitySheetSymbol == entity.target) { println("[STOCKCHAT_DBG] handleStockEntityLongPress ignored(dup pending)"); return }

        // "start" fires while the finger is still down. Presenting the sheet
        // there means the eventual finger-up lands on the freshly mounted card
        // and bleeds through into its stock header, jumping straight to the
        // detail page. Always wait for the gesture to actually finish.
        if (state == "start") {
            println("[STOCKCHAT_DBG] handleStockEntityLongPress deferred(start) waiting for end")
            pendingLongPressSymbol = entity.target
            // Fallback for bridges that never emit "end" for a long press.
            setTimeout(700) {
                if (pendingLongPressSymbol != entity.target) return@setTimeout
                pendingLongPressSymbol = ""
                openSheetOnGestureEnd(entity)
            }
            return
        }
        if (state != "end") { println("[STOCKCHAT_DBG] handleStockEntityLongPress ignored(state=$state)"); return }
        pendingLongPressSymbol = ""
        openSheetOnGestureEnd(entity)
    }

    private fun openSheetOnGestureEnd(entity: EntitySpan) {
        println("[STOCKCHAT_DBG] handleStockEntityLongPress -> dispatch SHEET for ${entity.target}")
        suppressNextStockClickSymbol = entity.target
        handleStockEntity(entity, EntityAction.SHEET)
        setTimeout(250) {
            if (suppressNextStockClickSymbol == entity.target) {
                suppressNextStockClickSymbol = ""
            }
        }
'''

# detect newline style
nl = "\r\n" if lines[idx].endswith("\r\n") else "\n"

new_lines = NEW_BODY.split("\n")
replacement = [(l + nl) for l in new_lines[:-1]]
if new_lines[-1] != "":
    replacement.append(new_lines[-1] + nl)

# keep the original closing brace line (end)
tail = lines[end:]  # starts with "    }"
lines = lines[:idx] + replacement + tail

with io.open(PATH, "w", encoding="utf-8", newline="") as f:
    f.writelines(lines)

print("PATCHED ok")
