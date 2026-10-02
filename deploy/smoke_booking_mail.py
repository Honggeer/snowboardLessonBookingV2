"""Local Mailpit smoke for the two booking notifications and their guarded links."""

import getpass
import json
import os
import re
import time
import uuid
from datetime import datetime, timedelta
from http.cookiejar import CookieJar
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import HTTPCookieProcessor, Request, build_opener, urlopen
from zoneinfo import ZoneInfo


base = os.environ.get("SMOKE_BASE_URL", "http://localhost:8088").rstrip("/")
mailpit = os.environ.get("MAILPIT_URL", "http://localhost:8025").rstrip("/")
coach_email = os.environ.get("SMOKE_COACH_EMAIL") or input("Local test coach email: ").strip()
coach_password = os.environ.get("SMOKE_COACH_PASSWORD") or getpass.getpass("Local test coach password: ")
student_email = "booking-smoke-" + uuid.uuid4().hex[:12] + "@example.test"
student_password = "local booking smoke password 2026"


class Client:
    def __init__(self):
        self.opener = build_opener(HTTPCookieProcessor(CookieJar()))
        self.csrf = None

    def get(self, path):
        with self.opener.open(base + path, timeout=10) as response:
            return json.load(response)

    def post(self, path, body, key=None):
        if self.csrf is None:
            self.csrf = self.get("/api/auth/csrf")
        headers = {"Content-Type": "application/json", self.csrf["headerName"]: self.csrf["token"]}
        if key:
            headers["Idempotency-Key"] = key
        request = Request(base + path, data=json.dumps(body).encode(), headers=headers, method="POST")
        with self.opener.open(request, timeout=10) as response:
            return json.load(response)


def mail_for(email, needle):
    url = mailpit + "/view/latest.txt?query=" + quote("to:" + email)
    for _ in range(35):
        try:
            with urlopen(url, timeout=5) as response:
                body = response.read().decode()
            if needle in body:
                return body
        except HTTPError as error:
            if error.code != 404:
                raise
        time.sleep(1)
    raise AssertionError("Expected booking mail did not arrive in local Mailpit")


student = Client()
student.post("/api/auth/register", {"name": "Booking Smoke", "level": "入门",
                                    "email": student_email, "password": student_password})
verification = mail_for(student_email, "#verify?token=")
token = re.search(r"#verify\?token=([^\s]+)", verification)
assert token, "Verification mail has no token"
student.post("/api/auth/email-verification", {"token": token.group(1)})
assert student.post("/api/auth/login", {
    "email": student_email, "password": student_password,
})["role"] == "STUDENT"
student.csrf = student.get("/api/auth/csrf")

coach = Client()
assert coach.post("/api/auth/login", {
    "email": coach_email, "password": coach_password,
})["role"] == "COACH"
coach.csrf = coach.get("/api/auth/csrf")
suffix = uuid.uuid4().hex[:8]
mountain = coach.post("/api/coach/mountains", {"name": "Mailpit Test " + suffix}, str(uuid.uuid4()))
course = coach.post("/api/coach/courses", {
    "title": "Mailpit Test " + suffix, "description": "Local mail smoke",
    "priceAmount": "150.00", "currency": "CAD",
}, str(uuid.uuid4()))

chosen = None
today = datetime.now(ZoneInfo("America/Toronto")).date()
for offset in range(10, 32):
    date = today + timedelta(days=offset)
    month = coach.get(f"/api/coach/availability/month?year={date.year}&month={date.month}")
    if any(day["localDate"] == date.isoformat() for day in month["days"]):
        continue
    chosen = date.isoformat()
    break
assert chosen, "No empty coach date was found in the next 31 days"
availability = coach.post("/api/coach/availability/batches", {
    "days": [{"localDate": chosen, "startTime": "10:00", "endTime": "12:00"}],
}, str(uuid.uuid4()))
slot_id = availability["slots"][0]["id"]

booking = student.post("/api/bookings", {
    "courseId": course["id"], "slotId": slot_id, "mountainId": mountain["id"],
}, str(uuid.uuid4()))
assert booking["status"] == "PENDING"
coach_mail = mail_for(coach_email, booking["id"])
assert "/#/coach-applications/" + booking["id"] in coach_mail
assert coach.get("/api/coach/bookings/" + booking["id"])["status"] == "PENDING"

confirmed = coach.post("/api/coach/bookings/" + booking["id"] + "/confirm", {})
assert confirmed["status"] == "CONFIRMED"
student_mail = mail_for(student_email, booking["id"])
assert "/#/my-bookings/" + booking["id"] in student_mail
assert "至少 24 小时前取消" in student_mail
assert student.get("/api/bookings/" + booking["id"])["status"] == "CONFIRMED"
try:
    student.get("/api/coach/bookings/" + booking["id"])
    raise AssertionError("Student unexpectedly read coach booking endpoint")
except HTTPError as error:
    assert error.code == 403

student.post("/api/bookings/" + booking["id"] + "/cancel", {})
coach.post("/api/coach/courses/" + course["id"] + "/archive", {})
coach.post("/api/coach/mountains/" + mountain["id"] + "/deactivate", {})
print("Local booking application mail, confirmation mail, guarded links and cleanup: OK")
