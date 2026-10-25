#!/usr/bin/env python3
"""Posts an Info developer card to Firestore announcements/ (Python standard library only).

    python3 tools/post_announcement.py --type update --version 0.19.0 --title "Judul" --body "Isi"
    python3 tools/post_announcement.py --delete <id>

--type: update, bugfix, or info. --body-file reads the text from a file instead of --body.
Uses the firebase-tools login on this machine (~/.config/configstore/firebase-tools.json).
"""
import argparse
import datetime
import json
import os
import urllib.parse
import urllib.request

PROJECT = "valoid-d04fc"
BASE = f"https://firestore.googleapis.com/v1/projects/{PROJECT}/databases/(default)/documents/announcements"
# Public OAuth client of firebase-tools.
CLIENT_ID = "563584335869-fgrhgmd47bqnekij5i8b5pr03ho849e6.apps.googleusercontent.com"
CLIENT_SECRET = "j9iVZfS8kkCEFUPaAeJV0sAi"


def access_token():
    with open(os.path.expanduser("~/.config/configstore/firebase-tools.json")) as handle:
        refresh = json.load(handle)["tokens"]["refresh_token"]
    data = urllib.parse.urlencode({"client_id": CLIENT_ID, "client_secret": CLIENT_SECRET,
                                   "grant_type": "refresh_token", "refresh_token": refresh}).encode()
    with urllib.request.urlopen("https://oauth2.googleapis.com/token", data) as response:
        return json.load(response)["access_token"]


def call(method, url, body=None):
    request = urllib.request.Request(url, json.dumps(body).encode() if body else None, method=method,
                                     headers={"Authorization": f"Bearer {access_token()}", "Content-Type": "application/json"})
    with urllib.request.urlopen(request) as response:
        return json.load(response)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--type", choices=["update", "bugfix", "info"], default="info")
    parser.add_argument("--title")
    parser.add_argument("--body")
    parser.add_argument("--body-file")
    parser.add_argument("--version")
    parser.add_argument("--delete")
    args = parser.parse_args()
    if args.delete:
        call("DELETE", f"{BASE}/{args.delete}")
        print("DELETED", args.delete)
        return
    body = open(args.body_file, encoding="utf-8").read().strip() if args.body_file else args.body
    if not args.title or not body:
        parser.error("--title dan --body/--body-file wajib diisi")
    fields = {
        "title": {"stringValue": args.title},
        "body": {"stringValue": body},
        "type": {"stringValue": args.type},
        "publishedAt": {"timestampValue": datetime.datetime.now(datetime.timezone.utc).isoformat()},
    }
    if args.version:
        fields["version"] = {"stringValue": args.version}
    doc = call("POST", BASE, {"fields": fields})
    print("POSTED", doc["name"].rsplit("/", 1)[-1])


if __name__ == "__main__":
    main()
