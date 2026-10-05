"""Installed timer launcher selects the active version's reviewed backup program."""
import json
import os
import re
import sys
from pathlib import Path
from release import load_config

config_path = "/etc/snowboard-v2/host.json"
config = load_config(config_path)
version = json.loads((Path(config["state_dir"]) / "current.json").read_text())
if not re.fullmatch(r"[0-9a-f]{40}", version["sha"]):
    raise ValueError("invalid active release")
bundle = Path(config["releases_dir"]) / version["sha"]
if Path(version["bundle"]).resolve() != bundle.resolve():
    raise ValueError("active backup program outside configured releases")
os.execv(sys.executable, [sys.executable, str(bundle / "deploy/backup.py"), "run", "--config", config_path])
