import copy
import json

import yt_dlp
from yt_dlp.extractor.youtube._base import INNERTUBE_CLIENTS

VISIONOS = "visionos"
VISIONOS_USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15"
    " (KHTML, like Gecko) Version/18.0 Safari/605.1.15"
)


def _register_visionos():
    """Teach yt-dlp the one client that can still serve a whole song.

    Every client yt-dlp ships is byte-capped today: its default pick, ANDROID_VR, answers 403 to
    every range past the first 512 KiB, and the WEB family answers the same way even when handed a
    real BotGuard PO token. VISIONOS is not capped, which is why the app's own playback chain puts
    it first, so the fallback borrows it rather than starting a track it cannot finish.

    ANDROID_VR is the template because it already declares what VISIONOS needs: no PO token, no JS
    player, no auth.
    """
    if VISIONOS in INNERTUBE_CLIENTS:
        return

    client = copy.deepcopy(INNERTUBE_CLIENTS["android_vr"])
    client["INNERTUBE_CONTEXT"]["client"] = {
        "clientName": "VISIONOS",
        "clientVersion": "0.1",
        "deviceMake": "Apple",
        "deviceModel": "RealityDevice14,1",
        "osName": "visionOS",
        "osVersion": "1.3.21O771",
        "hl": "en",
        "userAgent": VISIONOS_USER_AGENT,
    }
    client["INNERTUBE_CONTEXT_CLIENT_NAME"] = 101
    INNERTUBE_CLIENTS[VISIONOS] = client


def download(quickjs_bin: str, video_id: str, visitor_data: str = "") -> str:
    _register_visionos()

    base = {"format": "bestaudio", "js_runtimes": {"quickjs": {"path": quickjs_bin}}}
    youtube_args = {"player_client": [VISIONOS]}
    if visitor_data:
        youtube_args["visitor_data"] = [visitor_data]

    # A capped URL is worse than no URL: playback starts, then stops for good half a minute in and
    # no re-resolve fixes it. So the default clients are asked only once VISIONOS refuses outright.
    try:
        opts = dict(base, extractor_args={"youtube": youtube_args})
        info = yt_dlp.YoutubeDL(opts).extract_info(video_id, download=False)
    except yt_dlp.utils.DownloadError:
        info = yt_dlp.YoutubeDL(base).extract_info(video_id, download=False)

    return json.dumps(info, indent=4)


def upgrade(package_name):
    try:
        import ensurepip

        ensurepip.bootstrap()
    except Exception as e:
        print(f"Error running ensurepip: ${e}")

    try:
        import pip
        from pip._internal import main as pip_main

        pip_main(["install", "--upgrade", package_name])
        print(f"Successfully upgraded {package_name}")
    except Exception as e:
        print(f"Error upgrading package {package_name}: {e}")
