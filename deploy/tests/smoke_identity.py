"""Local integration smoke: creates one throwaway student in MySQL and Mailpit."""

import json
import os
import re
import subprocess
import time
import uuid
from http.cookiejar import CookieJar
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import HTTPCookieProcessor, Request, build_opener, urlopen


base = os.environ.get("SMOKE_BASE_URL", "http://localhost:8088").rstrip("/")
mailpit = os.environ.get("MAILPIT_URL", "http://localhost:8025").rstrip("/")
client = build_opener(HTTPCookieProcessor(CookieJar()))
email = "smoke-" + uuid.uuid4().hex[:12] + "@example.test"
password = "local smoke password 雪山 2026"


def get(path):
    with client.open(base + path, timeout=10) as response:
        return json.load(response)


def post(path, body, csrf):
    request = Request(
        base + path,
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json", csrf["headerName"]: csrf["token"]},
        method="POST",
    )
    with client.open(request, timeout=10) as response:
        return json.load(response)


csrf = get("/api/auth/csrf")
post("/api/auth/register", {"name": "Local Smoke", "level": "入门",
                             "email": email, "password": password}, csrf)
mail = None
for _ in range(30):
    try:
        url = mailpit + "/view/latest.txt?query=" + quote("to:" + email)
        with urlopen(url, timeout=5) as response:
            mail = response.read().decode()
            break
    except HTTPError as error:
        if error.code != 404:
            raise
    time.sleep(1)
assert mail, "No verification email arrived in local Mailpit within 30 seconds"
token = re.search(r"#verify\?token=([^\s]+)", mail)
assert token, "Verification link missing from sandbox email"
post("/api/auth/email-verification", {"token": token.group(1)}, csrf)
account = post("/api/auth/login", {"email": email, "password": password}, csrf)
assert account["role"] == "STUDENT" and account["name"] == "Local Smoke"
assert get("/api/auth/me")["id"] == account["id"]
if os.environ.get("SMOKE_RESTART_BACKEND") == "1":
    subprocess.run(["docker-compose", "-f", "deploy/compose.yaml", "restart", "backend"],
                   check=True, stdout=subprocess.DEVNULL)
    for _ in range(30):
        try:
            with urlopen(base + "/api/actuator/health", timeout=2) as response:
                if json.load(response)["status"] == "UP":
                    break
        except Exception:
            time.sleep(1)
    assert get("/api/auth/me")["id"] == account["id"], "Session did not survive backend restart"
post("/api/auth/logout", {}, get("/api/auth/csrf"))
try:
    get("/api/auth/me")
    raise AssertionError("Session remained valid after logout")
except HTTPError as error:
    assert error.code == 401
print("Local register, SMTP delivery, verify, login, session and logout: OK"
      + ("; backend restart: OK" if os.environ.get("SMOKE_RESTART_BACKEND") == "1" else ""))
