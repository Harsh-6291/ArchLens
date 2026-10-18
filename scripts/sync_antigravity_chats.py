"""
Antigravity Chat Auto-Synchronizer & Recovery Tool
Scans all conversations in the Antigravity Brain storage and ensures
they are properly indexed in both global and workspace SQLite state databases.
"""

import os
import sqlite3
import json
import datetime

BRAIN_DIR = os.path.expanduser(r"~\.gemini\antigravity-ide\brain")
ROAMING_DIR = os.path.expandvars(r"%APPDATA%\Antigravity IDE\User")
GLOBAL_DB = os.path.join(ROAMING_DIR, "globalStorage", "state.vscdb")
WS_STORAGE_DIR = os.path.join(ROAMING_DIR, "workspaceStorage")

def discover_conversations():
    discovered = {}
    if not os.path.exists(BRAIN_DIR):
        return discovered

    for session_id in os.listdir(BRAIN_DIR):
        session_path = os.path.join(BRAIN_DIR, session_id)
        if not os.path.isdir(session_path):
            continue

        transcript_file = os.path.join(session_path, ".system_generated", "logs", "transcript.jsonl")
        if not os.path.exists(transcript_file) or os.path.getsize(transcript_file) == 0:
            continue

        mtime = int(os.path.getmtime(transcript_file) * 1000)
        title = f"Chat {session_id[:8]}"

        try:
            with open(transcript_file, 'r', encoding='utf-8') as f:
                for line in f:
                    try:
                        record = json.loads(line)
                        if record.get("type") == "USER_INPUT":
                            raw_content = record.get("content", "")
                            # Clean up XML tags like <USER_REQUEST>
                            lines = [l.strip() for l in raw_content.split("\n") 
                                     if l.strip() and not l.strip().startswith("<") and not l.strip().endswith(">")]
                            if lines:
                                title = lines[0][:60]
                            break
                    except Exception:
                        pass
        except Exception as e:
            print(f"Error reading {session_id}: {e}")

        discovered[session_id] = {
            "sessionId": session_id,
            "title": title,
            "lastModified": mtime,
            "isStale": False
        }

    return discovered

def update_db(db_path, discovered_entries):
    if not os.path.exists(db_path):
        return 0

    try:
        con = sqlite3.connect(db_path)
        cur = con.cursor()
        res = cur.execute("SELECT value FROM ItemTable WHERE key = 'chat.ChatSessionStore.index'").fetchone()

        data = {"version": 1, "entries": {}}
        if res and res[0]:
            try:
                data = json.loads(res[0])
            except Exception:
                pass

        if "entries" not in data or not isinstance(data["entries"], dict):
            data["entries"] = {}

        # Merge discovered entries
        updated = False
        for sid, entry in discovered_entries.items():
            if sid not in data["entries"]:
                data["entries"][sid] = entry
                updated = True
            else:
                # Update title/lastModified if default or empty
                if not data["entries"][sid].get("title") or data["entries"][sid]["title"].startswith("Chat "):
                    data["entries"][sid]["title"] = entry["title"]
                    updated = True

        if updated:
            cur.execute("INSERT OR REPLACE INTO ItemTable (key, value) VALUES ('chat.ChatSessionStore.index', ?)",
                        (json.dumps(data),))
            con.commit()
        con.close()
        return len(data["entries"])
    except Exception as e:
        print(f"Failed to update {db_path}: {e}")
        return 0

def sync_all():
    conversations = discover_conversations()
    print(f"Found {len(conversations)} active conversation transcripts in Antigravity Brain.")

    # 1. Update global storage
    g_count = update_db(GLOBAL_DB, conversations)
    print(f"Global storage synchronized: {g_count} chats indexed.")

    # 2. Update all workspace storages (e.g., ArchLens workspaces)
    if os.path.exists(WS_STORAGE_DIR):
        for ws in os.listdir(WS_STORAGE_DIR):
            ws_path = os.path.join(WS_STORAGE_DIR, ws, "state.vscdb")
            if os.path.exists(ws_path):
                update_db(ws_path, conversations)
    print("All workspace storages synchronized successfully.")

if __name__ == "__main__":
    sync_all()
