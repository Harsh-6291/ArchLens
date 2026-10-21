#!/usr/bin/env python3
"""
Quickstart verification script for ArchLens.
Checks environment prerequisites (Docker, Java, Node.js), tests container connectivity,
and verifies backend and frontend build readiness.
"""

import os
import shutil
import subprocess
import sys

def check_command(cmd, name):
    path = shutil.which(cmd)
    if path:
        print(f"  [OK] {name} is installed: {path}")
        return True
    else:
        print(f"  [ERROR] {name} is NOT found in PATH. Please install {name}.", file=sys.stderr)
        return False

def check_docker_containers():
    print("\nChecking Docker infrastructure containers...")
    cmd = ["docker", "ps", "--filter", "name=archlens", "--format", "{{.Names}} - {{.Status}}"]
    try:
        res = subprocess.run(cmd, capture_output=True, text=True, check=True)
        lines = res.stdout.strip().splitlines()
        if lines and any(lines):
            for line in lines:
                print(f"  [RUNNING] {line}")
            return True
        else:
            print("  [WARN] No ArchLens containers found running. Run 'docker-compose up -d' to start Postgres, Redis, and RabbitMQ.")
            return False
    except Exception as e:
        print(f"  [WARN] Docker check failed: {e}")
        return False

def check_backend_build():
    print("\nVerifying Backend tests and compilation...")
    backend_dir = os.path.join(os.path.dirname(__file__), "..", "backend")
    gradle_cmd = os.path.join(backend_dir, "gradlew.bat" if os.name == "nt" else "gradlew")
    try:
        res = subprocess.run([gradle_cmd, "classes", "testClasses"], cwd=backend_dir, capture_output=True, text=True)
        if res.returncode == 0:
            print("  [OK] Backend classes and testClasses compiled successfully.")
            return True
        else:
            print(f"  [ERROR] Backend compilation failed:\n{res.stderr}", file=sys.stderr)
            return False
    except Exception as e:
        print(f"  [ERROR] Failed to run Gradle: {e}", file=sys.stderr)
        return False

def check_frontend_build():
    print("\nVerifying Frontend Next.js build readiness...")
    frontend_dir = os.path.join(os.path.dirname(__file__), "..", "frontend")
    npm_cmd = "npm.cmd" if os.name == "nt" else "npm"
    try:
        res = subprocess.run([npm_cmd, "run", "build"], cwd=frontend_dir, capture_output=True, text=True)
        if res.returncode == 0:
            print("  [OK] Frontend Next.js production build succeeded with 0 errors.")
            return True
        else:
            print(f"  [ERROR] Frontend build failed:\n{res.stderr}", file=sys.stderr)
            return False
    except Exception as e:
        print(f"  [ERROR] Failed to run npm build: {e}", file=sys.stderr)
        return False

def main():
    print("=" * 60)
    print("  ArchLens — Quickstart Verification")
    print("=" * 60)

    print("\nChecking tools and runtimes:")
    ok = True
    ok &= check_command("docker", "Docker")
    ok &= check_command("java", "Java 21 JDK")
    ok &= check_command("node", "Node.js")
    ok &= check_command("npm", "npm")

    check_docker_containers()
    backend_ok = check_backend_build()
    frontend_ok = check_frontend_build()

    print("\n" + "=" * 60)
    if ok and backend_ok and frontend_ok:
        print("  Summary: All system prerequisites and builds verified! [PASS]")
        print("  Start full stack:")
        print("    1. docker-compose up -d")
        print("    2. cd backend && ./gradlew bootRun")
        print("    3. cd frontend && npm run dev")
    else:
        print("  Summary: Some checks did not pass. Review output above.")
    print("=" * 60)

if __name__ == "__main__":
    main()
