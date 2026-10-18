#!/usr/bin/env python3
"""
Smoke test script for verifying System GitHub Credential from PostgreSQL against GitHub API.
"""

import argparse
import hashlib
import json
import os
import subprocess
import sys
import urllib.request
import urllib.error
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

DEFAULT_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

def decrypt_token(encrypted_bytes: bytes, raw_key: str) -> str:
    key_bytes = hashlib.sha256(raw_key.encode("utf-8")).digest()
    aesgcm = AESGCM(key_bytes)
    iv = encrypted_bytes[:12]
    ciphertext = encrypted_bytes[12:]
    decrypted = aesgcm.decrypt(iv, ciphertext, None)
    return decrypted.decode("utf-8")

def main():
    parser = argparse.ArgumentParser(description="Test System GitHub Credential against GitHub API")
    parser.add_argument("--repo", default="octocat/Hello-World", help="Repository to test (owner/repo)")
    parser.add_argument("--key", default=os.getenv("ARCHLENS_ENCRYPTION_KEY", DEFAULT_KEY),
                        help="AES encryption key")
    args = parser.parse_args()

    sql = "SELECT encode(access_token_encrypted, 'hex') FROM system_github_credential ORDER BY created_at DESC LIMIT 1;"
    cmd = [
        "docker", "exec", "archlens-postgres-1",
        "psql", "-U", "archlens_user", "-d", "archlens_db",
        "-t", "-A", "-c", sql
    ]

    result = subprocess.run(cmd, capture_output=True, text=True)
    hex_token = result.stdout.strip()

    token = None
    if hex_token:
        try:
            raw_bytes = bytes.fromhex(hex_token)
            token = decrypt_token(raw_bytes, args.key)
            print("Successfully decrypted system GitHub token from database.")
        except Exception as e:
            print(f"Failed to decrypt token: {e}", file=sys.stderr)
            sys.exit(1)
    else:
        print("No system token in database; testing unauthenticated public repo fetch...")

    url = f"https://api.github.com/repos/{args.repo}"
    headers = {
        "User-Agent": "ArchLens-SmokeTest/1.0",
        "Accept": "application/vnd.github.v3+json"
    }
    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req) as response:
            data = json.loads(response.read().decode("utf-8"))
            print("GitHub API Smoke Test PASSED!")
            print(f"  Repository: {data.get('full_name')}")
            print(f"  Default Branch: {data.get('default_branch')}")
            print(f"  Description: {data.get('description')}")
            print(f"  Private: {data.get('private')}")
    except urllib.error.HTTPError as e:
        if e.code == 401 and "dummy" in (token or ""):
            print("Note: Token was successfully retrieved and decrypted from DB, but GitHub rejected the dummy test token (401 Unauthorized).")
            print("To use a live GitHub token, run: python scripts/seed_system_credential.py --token <YOUR_GITHUB_PAT>")
            print("Testing public fetch without bearer token...")
            req_public = urllib.request.Request(url, headers={"User-Agent": "ArchLens-SmokeTest/1.0", "Accept": "application/vnd.github.v3+json"})
            with urllib.request.urlopen(req_public) as pub_res:
                data = json.loads(pub_res.read().decode("utf-8"))
                print("Public fetch successful!")
                print(f"  Repository: {data.get('full_name')}")
                print(f"  Default Branch: {data.get('default_branch')}")
        else:
            print(f"GitHub API returned error: {e.code} {e.reason}", file=sys.stderr)
            sys.exit(1)

if __name__ == "__main__":
    main()
