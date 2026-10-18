import json
import os

settings_path = os.path.expandvars(r"%APPDATA%\Antigravity IDE\User\settings.json")
if os.path.exists(settings_path):
    with open(settings_path, 'r', encoding='utf-8') as f:
        data = json.load(f)
else:
    data = {}

data["window.restoreWindows"] = "all"
data["workbench.editor.restoreEditors"] = True

with open(settings_path, 'w', encoding='utf-8') as f:
    json.dump(data, f, indent=4)

print("Updated settings successfully!")
