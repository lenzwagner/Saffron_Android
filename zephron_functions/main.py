from firebase_functions import https_fn, scheduler_fn, options
from firebase_admin import initialize_app, firestore
import instaloader
import json
import os
import base64
import tempfile
import time

initialize_app()

SESSION_FILE = "/tmp/session-insta"
SESSION_MAX_AGE = 7 * 24 * 3600  # 7 days in seconds

INSTA_USER = os.environ.get("INSTA_USER", "")
INSTA_PW   = os.environ.get("INSTA_PW", "")

L = instaloader.Instaloader()
_session_loaded = False

def _load_session_from_secret():
    """Decode INSTA_SESSION_B64 secret into /tmp and load it."""
    b64 = os.environ.get("INSTA_SESSION_B64", "")
    if not b64:
        return False
    try:
        data = base64.b64decode(b64)
        with open(SESSION_FILE, "wb") as f:
            f.write(data)
        L.load_session_from_file(INSTA_USER, SESSION_FILE)
        print("Session loaded from secret.")
        return True
    except Exception as e:
        print(f"Failed to load session from secret: {e}")
        return False

def _login_fresh():
    """Login with username/password and save session to /tmp."""
    if not INSTA_USER or not INSTA_PW:
        return False
    try:
        L.login(INSTA_USER, INSTA_PW)
        L.save_session_to_file(SESSION_FILE)
        print(f"Fresh login successful for {INSTA_USER}.")
        return True
    except Exception as e:
        print(f"Fresh login failed: {e}")
        return False

def _session_age_seconds():
    try:
        return time.time() - os.path.getmtime(SESSION_FILE)
    except Exception:
        return float("inf")

def ensure_session():
    global _session_loaded
    if _session_loaded and _session_age_seconds() < SESSION_MAX_AGE:
        return True
    # Try secret first, fall back to fresh login
    if _load_session_from_secret():
        _session_loaded = True
        return True
    if _login_fresh():
        _session_loaded = True
        return True
    return False

# Load session eagerly at cold start
ensure_session()

@https_fn.on_request(
    memory=options.MemoryOption.GB_1,
    timeout_sec=120,
    secrets=["INSTA_SESSION_B64", "INSTA_USER", "INSTA_PW"]
)
def get_insta_recipe(req):
    if req.method == "OPTIONS":
        return https_fn.Response("", status=204,
            headers={"Access-Control-Allow-Origin": "*",
                     "Access-Control-Allow-Methods": "POST",
                     "Access-Control-Allow-Headers": "Content-Type"})

    if req.method != "POST":
        return https_fn.Response("Use POST", status=405)

    data = req.get_json(silent=True)
    if not data:
        return https_fn.Response("Invalid JSON", status=400)

    shortcode = data.get("shortcode")
    if not shortcode:
        return https_fn.Response("No shortcode", status=400)

    # Renew session if expired
    if not ensure_session():
        return https_fn.Response(
            json.dumps({"error": "Instagram-Session konnte nicht erneuert werden.", "success": False}),
            status=500, mimetype="application/json")

    try:
        post = instaloader.Post.from_shortcode(L.context, shortcode)
        result = {
            "author": post.owner_username or "",
            "description": post.caption or "",
            "thumbnail_url": post.url or "",
            "video_url": post.video_url if post.is_video else "",
            "success": True
        }
        return https_fn.Response(json.dumps(result), mimetype="application/json")

    except Exception as e:
        error_msg = str(e)
        session_errors = ["401", "login", "NoneType", "not subscriptable", "checkpoint", "Bad credentials", "LoginRequired"]
        if any(s in error_msg for s in session_errors):
            global _session_loaded
            _session_loaded = False
            user_msg = "Instagram-Session abgelaufen. Bitte später erneut versuchen."
        elif "not found" in error_msg.lower() or "404" in error_msg:
            user_msg = "Beitrag nicht gefunden oder privat."
        else:
            user_msg = f"Instagram-Fehler: {error_msg}"

        print(f"[ERROR] shortcode={shortcode}: {error_msg}")
        return https_fn.Response(
            json.dumps({"error": user_msg, "success": False}),
            status=500, mimetype="application/json")


@scheduler_fn.on_schedule(
    schedule="every 7 days",
    secrets=["INSTA_SESSION_B64", "INSTA_USER", "INSTA_PW"]
)
def scheduled_insta_refresh(event: scheduler_fn.ScheduledEvent) -> None:
    """Renews Instagram session every 7 days via Cloud Scheduler."""
    global _session_loaded
    _session_loaded = False
    success = _login_fresh()
    print(f"Scheduled session renewal: {'OK' if success else 'FAILED'}")
