"""Check the same-origin entry point after the local Compose stack is ready."""

import json
import os
import sys
from base64 import b64encode
from http.cookiejar import CookieJar
from urllib.error import HTTPError
from urllib.request import HTTPCookieProcessor
from urllib.request import Request
from urllib.request import build_opener
from urllib.request import urlopen


base = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://localhost:8088"

with urlopen(base + "/", timeout=10) as response:
    page = response.read().decode("utf-8")
    assert response.status == 200
    assert "约课平台 V2" in page

with urlopen(base + "/api/actuator/health", timeout=10) as response:
    health = json.load(response)
    assert response.status == 200
    assert health["status"] == "UP"

if password := os.environ.get("SMOKE_BASIC_PASSWORD"):
    username = os.environ.get("SMOKE_BASIC_USER", "user")
    credentials = b64encode(f"{username}:{password}".encode()).decode()
    request = Request(base + "/api/demo/time-window-previews",
                      headers={"Authorization": f"Basic {credentials}"})
    try:
        urlopen(request, timeout=10)
    except HTTPError as error:
        assert error.code == 405, f"Expected routed demo endpoint (405), got {error.code}"
    else:
        raise AssertionError("Demo endpoint unexpectedly accepted GET")

    opener = build_opener(HTTPCookieProcessor(CookieJar()))
    csrf_request = Request(base + "/api/demo/time-window-previews/csrf",
                           headers={"Authorization": f"Basic {credentials}"})
    with opener.open(csrf_request, timeout=10) as response:
        csrf = json.load(response)
        assert response.status == 200

    payload = json.dumps({"start": "2026-12-01T14:00:00Z",
                          "end": "2026-12-01T15:30:00Z"}).encode()
    create_request = Request(base + "/api/demo/time-window-previews", data=payload,
                             headers={"Authorization": f"Basic {credentials}",
                                      "Content-Type": "application/json",
                                      csrf["headerName"]: csrf["token"]},
                             method="POST")
    with opener.open(create_request, timeout=10) as response:
        preview = json.load(response)
        location = response.headers["Location"]
        assert response.status == 201
        assert preview["durationSeconds"] == 5400

    find_request = Request(base + location,
                           headers={"Authorization": f"Basic {credentials}"})
    with opener.open(find_request, timeout=10) as response:
        assert response.status == 200
        assert json.load(response)["id"] == preview["id"]

print("Frontend and same-origin backend health: OK")
