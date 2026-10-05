"""Local Mailpit smoke for password recovery and old-session revocation."""

import json
import os
import re
import time
import uuid
from http.cookiejar import CookieJar
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import HTTPCookieProcessor, Request, build_opener, urlopen


base = os.environ.get("SMOKE_BASE_URL", "http://localhost:8088").rstrip("/")
mailpit = os.environ.get("MAILPIT_URL", "http://localhost:8025").rstrip("/")
email = "recovery-smoke-" + uuid.uuid4().hex[:12] + "@example.test"
old_password = "old smoke password 雪山"
new_password = "new smoke password 雪山"


class Client:
    def __init__(self):
        self.opener = build_opener(HTTPCookieProcessor(CookieJar()))
        self.csrf = None

    def get(self, path):
        with self.opener.open(base + path, timeout=10) as response:
            return json.load(response)

    def refresh_csrf(self):
        self.csrf = self.get("/api/auth/csrf")

    def post(self, path, body):
        if self.csrf is None:
            self.refresh_csrf()
        request = Request(
            base + path,
            data=json.dumps(body).encode(),
            headers={"Content-Type": "application/json", self.csrf["headerName"]: self.csrf["token"]},
            method="POST",
        )
        with self.opener.open(request, timeout=10) as response:
            return json.load(response)


def mail_matching(pattern):
    url = mailpit + "/view/latest.txt?query=" + quote("to:" + email)
    for _ in range(30):
        try:
            with urlopen(url, timeout=5) as response:
                match = re.search(pattern, response.read().decode())
                if match:
                    return match.group(1)
        except HTTPError as error:
            if error.code != 404:
                raise
        time.sleep(1)
    raise AssertionError("Expected email did not arrive in Mailpit within 30 seconds")


def expect_status(code, action):
    try:
        action()
    except HTTPError as error:
        assert error.code == code, f"Expected HTTP {code}, got {error.code}"
        return
    raise AssertionError(f"Expected HTTP {code}")


first = Client()
first.post("/api/auth/register", {"name": "Recovery Smoke", "level": "入门",
                                   "email": email, "password": old_password})
token = mail_matching(r"#verify\?token=([^\s]+)")
first.post("/api/auth/email-verification", {"token": token})
assert first.post("/api/auth/login", {"email": email, "password": old_password})["role"] == "STUDENT"
second = Client()
assert second.post("/api/auth/login", {"email": email, "password": old_password})["role"] == "STUDENT"

recovery = Client()
recovery.post("/api/auth/password-recovery/request", {"email": email})
code = mail_matching(r"验证码是：([0-9]{8})")
recovery.post("/api/auth/password-recovery/verify", {"email": email, "code": code})
recovery.refresh_csrf()
expect_status(410, lambda: Client().post("/api/auth/password-recovery/complete", {
    "newPassword": new_password, "confirmPassword": new_password,
}))
recovery.post("/api/auth/password-recovery/complete", {
    "newPassword": new_password, "confirmPassword": new_password,
})
expect_status(401, lambda: first.get("/api/auth/me"))
expect_status(401, lambda: second.get("/api/auth/me"))
expect_status(401, lambda: recovery.get("/api/auth/me"))
expect_status(401, lambda: Client().post("/api/auth/login", {
    "email": email, "password": old_password,
}))
assert Client().post("/api/auth/login", {"email": email, "password": new_password})["role"] == "STUDENT"
print("Local Mailpit code delivery, password reset and old-session revocation: OK")
