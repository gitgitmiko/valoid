#!/usr/bin/env python3
"""Gives (or revokes) free Valoid Pro for a Valoid account via Firestore proGrants/{uid} (standard library only).

    python3 tools/grant_pro.py user@example.com --note "Developer"
    python3 tools/grant_pro.py user@example.com --revoke
    python3 tools/grant_pro.py --list

Uses the firebase-tools login on this machine (~/.config/configstore/firebase-tools.json).
"""
import argparse
import datetime
import json
import urllib.error

from post_announcement import PROJECT, call

DOCS = f"https://firestore.googleapis.com/v1/projects/{PROJECT}/databases/(default)/documents/proGrants"


def lookup_uid(email):
    result = call("POST", f"https://identitytoolkit.googleapis.com/v1/projects/{PROJECT}/accounts:lookup",
                  {"email": [email]})
    users = result.get("users") or []
    if not users:
        raise SystemExit(f"Akun Valoid dengan email {email} tidak ditemukan")
    return users[0]["localId"]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("email", nargs="?")
    parser.add_argument("--note", default="")
    parser.add_argument("--revoke", action="store_true")
    parser.add_argument("--list", action="store_true")
    args = parser.parse_args()
    if args.list:
        for doc in call("GET", DOCS).get("documents", []):
            fields = doc["fields"]
            print(doc["name"].rsplit("/", 1)[1], fields["email"]["stringValue"], fields.get("note", {}).get("stringValue", ""))
        return
    if not args.email:
        parser.error("email wajib diisi")
    uid = lookup_uid(args.email)
    if args.revoke:
        call("DELETE", f"{DOCS}/{uid}")
        print("REVOKED", args.email, uid)
        return
    call("PATCH", f"{DOCS}/{uid}", {"fields": {
        "email": {"stringValue": args.email},
        "note": {"stringValue": args.note},
        "grantedAt": {"timestampValue": datetime.datetime.now(datetime.timezone.utc).isoformat()},
    }})
    print("GRANTED", args.email, uid)


if __name__ == "__main__":
    try:
        main()
    except urllib.error.HTTPError as e:
        raise SystemExit(f"{e.code} {e.read().decode()}")
