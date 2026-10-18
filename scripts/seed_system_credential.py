#!/usr/bin/env python3
"""
Seed / Rotate ArchLens System GitHub Credential into PostgreSQL.
Encrypts the token using AES-256-GCM compatible with backend EncryptionService.
"""

import argparse
import hashlib
import os
import subprocess
import sys
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

DEFAULT_KEY = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

def encrypt_token(token: str, raw_key: str) -> bytes:
    key_bytes = hashlib.sha256(raw_key.encode("utf-8")).digest()
    aesgcm = AESGCM(key_bytes)
    iv = os.urandom(12)
    ciphertext = aesgcm.encrypt(iv, token.encode("utf-8"), None)
    return iv + ciphertext

def main():
    parser = argparse.ArgumentParser(description="Seed ArchLens System GitHub Credential")
    parser.add_argument("--token", default=os.getenv("ARCHLENS_SYSTEM_GITHUB_TOKEN"),
                        help="GitHub App/PAT token for public repo imports")
    parser.add_argument("--key", default=os.getenv("ARCHLENS_ENCRYPTION_KEY", DEFAULT_KEY),
                        help="AES encryption key (matches backend)")
    args = parser.parse_args()

    if not args.token:
        print("Error: No GitHub token provided. Use --token or set ARCHLENS_SYSTEM_GITHUB_TOKEN.", file=sys.stderr)
        sys.exit(1)

    encrypted_bytes = encrypt_token(args.token, args.key)
    hex_str = "\\x" + encrypted_bytes.hex()

    sql = f"""
    INSERT INTO system_github_credential (access_token_encrypted, scopes, created_at)
    VALUES ('{hex_str}'::bytea, ARRAY['public_repo'], NOW());
    """

    cmd = [
        "docker", "exec", "archlens-postgres-1",
        "psql", "-U", "archlens_user", "-d", "archlens_db",
        "-c", sql
    ]

    print("Inserting encrypted system credential into PostgreSQL...")
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        print(f"Failed to insert credential: {result.stderr}", file=sys.stderr)
        sys.exit(1)

    print("System GitHub credential seeded successfully!")

if __name__ == "__main__":
    main()
