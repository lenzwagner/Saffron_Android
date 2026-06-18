from firebase_functions import https_fn, options
import instaloader
import json
import os

# Initialize Instaloader
L = instaloader.Instaloader()

# Load credentials from environment variables
INSTA_USER = os.environ.get("INSTA_USER")
INSTA_PW = os.environ.get("INSTA_PW")

def perform_login():
    if not INSTA_USER or not INSTA_PW:
        print("Instagram credentials not set. Proceeding anonymously.")
        return False

    session_file = f"/tmp/session-{INSTA_USER}"
    try:
        # Try to load existing session to avoid too many logins
        L.load_session_from_file(INSTA_USER, session_file)
        print(f"Session loaded for {INSTA_USER}")
        return True
    except FileNotFoundError:
        try:
            print(f"Logging in as {INSTA_USER}...")
            L.login(INSTA_USER, INSTA_PW)
            L.save_session_to_file(session_file)
            return True
        except Exception as e:
            print(f"Login failed: {e}")
            return False

# Attempt login once at startup
is_logged_in = perform_login()

@https_fn.on_request(
    memory=options.MemoryOption.GB_1,
    timeout_sec=120
)
def get_insta_recipe(req):
    global is_logged_in

    if req.method != 'POST':
        return https_fn.Response("Please use POST", status=405)

    data = req.get_json()
    if not data:
        return https_fn.Response("Invalid JSON", status=400)

    shortcode = data.get('shortcode')
    if not shortcode:
        return https_fn.Response("No shortcode provided", status=400)

    # Retry login if previously failed or if we suspect session is dead
    if not is_logged_in:
        is_logged_in = perform_login()

    try:
        post = instaloader.Post.from_shortcode(L.context, shortcode)

        result = {
            "author": post.owner_username,
            "description": post.caption,
            "thumbnail_url": post.url,
            "video_url": post.video_url,
            "success": True
        }
        return https_fn.Response(json.dumps(result), mimetype="application/json")

    except Exception as e:
        error_msg = str(e)
        # If we get a 401/Login error, try to relogin for next time
        if "401" in error_msg or "login" in error_msg.lower():
            is_logged_in = False

        return https_fn.Response(
            json.dumps({"error": error_msg, "success": False}),
            status=500,
            mimetype="application/json"
        )
