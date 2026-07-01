from firebase_functions import https_fn, scheduler_fn, options
from firebase_admin import initialize_app
import requests
import json
import os
import base64
import time

initialize_app()

COOKIES_FILE = "/tmp/insta_cookies"
COOKIES_MAX_AGE = 7 * 24 * 3600

_cookies: dict = {}
_cookies_loaded = False
_cookies_ts = 0.0

IG_APP_ID = "936619743392459"

def _shortcode_to_id(shortcode: str) -> int:
    table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    n = 0
    for c in shortcode:
        n = n * 64 + table.index(c)
    return n

def _load_cookies_from_secret() -> bool:
    b64 = os.environ.get("INSTA_SESSION_B64", "")
    if not b64:
        return False
    try:
        raw = base64.b64decode(b64)
        cookie_dict = json.loads(raw)
        global _cookies, _cookies_loaded, _cookies_ts
        _cookies = cookie_dict
        _cookies_loaded = True
        _cookies_ts = time.time()
        print("Cookies loaded from secret.")
        return True
    except Exception as e:
        print(f"Failed to load cookies from secret: {e}")
        return False

def _cookies_age() -> float:
    return time.time() - _cookies_ts

def ensure_cookies() -> bool:
    global _cookies_loaded
    if _cookies_loaded and _cookies_age() < COOKIES_MAX_AGE:
        return True
    return _load_cookies_from_secret()

def _ig_session() -> requests.Session:
    s = requests.Session()
    s.cookies.update(_cookies)
    s.headers.update({
        "User-Agent": "Instagram 275.0.0.27.98 Android",
        "X-IG-App-ID": IG_APP_ID,
    })
    return s

# Load cookies eagerly at cold start
ensure_cookies()

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

    if not ensure_cookies():
        return https_fn.Response(
            json.dumps({"error": "Instagram-Session konnte nicht geladen werden.", "success": False}),
            status=500, mimetype="application/json")

    try:
        media_id = _shortcode_to_id(shortcode)
        r = _ig_session().get(
            f"https://www.instagram.com/api/v1/media/{media_id}/info/",
            timeout=20
        )
        if r.status_code == 401:
            global _cookies_loaded
            _cookies_loaded = False
            return https_fn.Response(
                json.dumps({"error": "Instagram-Session abgelaufen. Bitte später erneut versuchen.", "success": False}),
                status=500, mimetype="application/json")

        if r.status_code != 200:
            return https_fn.Response(
                json.dumps({"error": f"Instagram Fehler {r.status_code}", "success": False}),
                status=500, mimetype="application/json")

        items = r.json().get("items", [])
        if not items:
            return https_fn.Response(
                json.dumps({"error": "Beitrag nicht gefunden oder privat.", "success": False}),
                status=404, mimetype="application/json")

        item = items[0]
        caption_obj = item.get("caption") or {}
        thumbnail_candidates = item.get("image_versions2", {}).get("candidates", [])
        video_versions = item.get("video_versions", [])

        result = {
            "author": item.get("user", {}).get("username", ""),
            "description": caption_obj.get("text", "") if isinstance(caption_obj, dict) else "",
            "thumbnail_url": thumbnail_candidates[0].get("url", "") if thumbnail_candidates else "",
            "video_url": video_versions[0].get("url", "") if video_versions else "",
            "success": True
        }
        return https_fn.Response(json.dumps(result), mimetype="application/json")

    except Exception as e:
        print(f"[ERROR] shortcode={shortcode}: {e}")
        return https_fn.Response(
            json.dumps({"error": f"Instagram-Fehler: {str(e)}", "success": False}),
            status=500, mimetype="application/json")


@scheduler_fn.on_schedule(
    schedule="0 3 * * 0",
    secrets=["INSTA_SESSION_B64", "INSTA_USER", "INSTA_PW"]
)
def scheduled_insta_refresh(event: scheduler_fn.ScheduledEvent) -> None:
    """Placeholder — session renewal handled via manual secret update."""
    print("scheduled_insta_refresh fired. Session renewal via secret update.")
