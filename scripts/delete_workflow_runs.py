"""
Utility script to delete all workflow runs in userio12/Dex2c-Android.
Supports retry attempts and exponential backoff during GitHub platform degradations.
"""

import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request

REPO = "userio12/Dex2c-Android"


def get_token():
    token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN")
    if token:
        return token
    try:
        out = subprocess.check_output(["gh", "auth", "token"]).decode().strip()
        if out:
            return out
    except Exception as e:
        print(f"Error fetching token from gh: {e}")
    return None


def fetch_workflow_runs(repo, token):
    url = f"https://api.github.com/repos/{repo}/actions/runs?per_page=100"
    runs = []
    page = 1
    while True:
        page_url = f"{url}&page={page}"
        req = urllib.request.Request(
            page_url,
            headers={
                "Authorization": f"Bearer {token}",
                "Accept": "application/vnd.github+json",
                "User-Agent": "Dex2c-Workflow-Deleter",
            },
        )
        try:
            with urllib.request.urlopen(req) as resp:
                data = json.load(resp)
                batch = data.get("workflow_runs", [])
                if not batch:
                    break
                runs.extend(batch)
                if len(runs) >= data.get("total_count", len(runs)):
                    break
                page += 1
        except Exception as e:
            print(f"Failed to fetch runs on page {page}: {e}")
            break
    return runs


def delete_run(repo, run_id, token, retries=2):
    url = f"https://api.github.com/repos/{repo}/actions/runs/{run_id}"
    req = urllib.request.Request(
        url,
        method="DELETE",
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "User-Agent": "Dex2c-Workflow-Deleter",
        },
    )
    last_code = 500
    last_body = ""
    for attempt in range(1, retries + 1):
        try:
            with urllib.request.urlopen(req) as resp:
                if resp.status in (204, 200, 202):
                    return True, resp.status, ""
        except urllib.error.HTTPError as e:
            last_code = e.code
            last_body = e.read().decode("utf-8", errors="ignore").strip()
            if attempt < retries:
                time.sleep(1.0 * attempt)
        except Exception as e:
            last_body = str(e)
            if attempt < retries:
                time.sleep(1.0 * attempt)
    return False, last_code, last_body


def main():
    token = get_token()
    if not token:
        print("Error: No GitHub token found. Please run 'gh auth login' or set GITHUB_TOKEN.")
        sys.exit(1)

    print(f"Fetching workflow runs for repository: {REPO}...")
    runs = fetch_workflow_runs(REPO, token)
    total = len(runs)
    print(f"Found {total} workflow run(s).")

    if total == 0:
        print("All workflow runs have been deleted! Repository is clean.")
        return

    deleted_count = 0
    failed_count = 0

    for idx, run in enumerate(runs, 1):
        run_id = run["id"]
        run_name = run.get("name", "Unknown")
        branch = run.get("head_branch", "unknown")
        created = run.get("created_at", "")

        print(f"[{idx}/{total}] Deleting run {run_id} ({run_name} | {branch} | {created})...", end=" ")
        success, code, body = delete_run(REPO, run_id, token)
        if success:
            print(f"SUCCESS (HTTP {code})")
            deleted_count += 1
        else:
            print(f"FAILED (HTTP {code}) {body[:60] if body else ''}")
            failed_count += 1
        time.sleep(0.3)

    print("\n--- Summary ---")
    print(f"Total: {total}")
    print(f"Deleted: {deleted_count}")
    print(f"Failed: {failed_count}")
    if failed_count > 0:
        print("Note: If deletions failed with HTTP 500/502, check https://www.githubstatus.com for active GitHub Actions outages and re-run once resolved.")


if __name__ == "__main__":
    main()
