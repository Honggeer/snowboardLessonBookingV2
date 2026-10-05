"""Read-only health and anonymous-auth smoke check for the local Compose stack."""

import json
import sys
from urllib.error import HTTPError
from urllib.request import urlopen


base = sys.argv[1].rstrip("/") if len(sys.argv) > 1 else "http://localhost:8088"

with urlopen(base + "/", timeout=10) as response:
    page = response.read().decode("utf-8")
    assert response.status == 200
    assert "约课平台 V2" in page

for image in ("geer-blue-desktop.png", "geer-blue-mobile-login.png", "geer-blue-mobile-register.png"):
    with urlopen(base + "/images/" + image, timeout=10) as response:
        assert response.status == 200
        assert response.headers.get_content_type() == "image/png"

with urlopen(base + "/api/actuator/health", timeout=10) as response:
    assert json.load(response)["status"] == "UP"

with urlopen(base + "/api/auth/csrf", timeout=10) as response:
    csrf = json.load(response)
    assert csrf["token"] and csrf["headerName"] == "X-CSRF-TOKEN"

try:
    urlopen(base + "/api/auth/me", timeout=10)
    raise AssertionError("Anonymous request unexpectedly accessed /api/auth/me")
except HTTPError as error:
    assert error.code == 401

print("Frontend, backend health, CSRF and anonymous access: OK")
