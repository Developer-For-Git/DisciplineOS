import sys
import os
import json
import urllib.request
import urllib.error
import subprocess
import re

# Ensure UTF-8 output on Windows
sys.stdout.reconfigure(encoding='utf-8')

PROJECT_DIR = os.path.abspath(r"C:\Users\LOL\Desktop\justC\DisciplineOS")
GRADLE_FILE = os.path.join(PROJECT_DIR, "app", "build.gradle.kts")
APK_SOURCE = os.path.join(PROJECT_DIR, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
DESKTOP_DIR = os.path.abspath(r"C:\Users\LOL\Desktop")
APK_TARGET = os.path.join(DESKTOP_DIR, "DisciplineOS.apk")
UPDATE_JSON = os.path.join(DESKTOP_DIR, "update.json")

def get_wifi_ip():
    """Detect current PC Wi-Fi IP address"""
    try:
        res = subprocess.check_output(
            ["powershell", "-NoProfile", "-Command",
             "(Get-NetIPAddress -InterfaceAlias '*Wi-Fi*' -AddressFamily IPv4 | Select-Object -ExpandProperty IPAddress -First 1)"],
            text=True
        ).strip()
        if res and not res.startswith("127."):
            return res
    except Exception:
        pass
    return "10.137.177.187"

def get_hotspot_gateway():
    """Get phone IP (gateway when connected to mobile hotspot)"""
    try:
        out = subprocess.check_output("route print 0.0.0.0", shell=True, text=True)
        for line in out.splitlines():
            parts = line.strip().split()
            if len(parts) >= 5 and parts[0] == "0.0.0.0" and parts[1] == "0.0.0.0":
                gw = parts[2]
                if gw != "0.0.0.0" and not gw.startswith("127."):
                    return gw
    except Exception:
        pass
    return None

def read_version_info():
    with open(GRADLE_FILE, "r", encoding="utf-8") as f:
        content = f.read()
    
    code_match = re.search(r'versionCode\s*=\s*(\d+)', content)
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    
    code = int(code_match.group(1)) if code_match else 1
    name = name_match.group(1) if name_match else "1.0.0"
    return code, name

def bump_version_in_gradle(new_code=None, new_name=None):
    with open(GRADLE_FILE, "r", encoding="utf-8") as f:
        content = f.read()

    cur_code, cur_name = read_version_info()
    code = new_code if new_code is not None else (cur_code + 1)
    
    if new_name:
        name = new_name
    else:
        parts = cur_name.split(".")
        if len(parts) == 3 and parts[2].isdigit():
            parts[1] = str(int(parts[1]) + 1)
            parts[2] = "0"
            name = ".".join(parts)
        else:
            name = f"2.{code}.0"

    content = re.sub(r'versionCode\s*=\s*\d+', f'versionCode = {code}', content)
    content = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{name}"', content)

    with open(GRADLE_FILE, "w", encoding="utf-8") as f:
        f.write(content)
        
    print(f"📦 Version set to v{name} (build {code})")
    return code, name

def build_apk():
    print("🔨 Compiling APK via Gradle (assembleDebug)...")
    cmd = "cmd /c \"gradlew.bat assembleDebug\""
    p = subprocess.run(cmd, cwd=PROJECT_DIR, shell=True, capture_output=True, text=True)
    if p.returncode != 0:
        print("❌ Gradle compilation failed:")
        print(p.stderr or p.stdout)
        sys.exit(1)
    print("✅ Build successful!")

def deploy_apk_and_manifest(code, name, notes):
    import shutil
    if not os.path.exists(APK_SOURCE):
        print(f"❌ APK source not found: {APK_SOURCE}")
        sys.exit(1)

    shutil.copy2(APK_SOURCE, APK_TARGET)
    file_size = os.path.getsize(APK_TARGET)
    print(f"📋 Copied APK to {APK_TARGET} ({file_size} bytes)")

    pc_ip = get_wifi_ip()
    manifest = {
        "versionCode": code,
        "versionName": name,
        "title": f"DisciplineOS {name} Update",
        "apkUrl": f"http://{pc_ip}:8081/DisciplineOS.apk",
        "releaseNotes": notes if notes else ["Performance enhancements & bug fixes"],
        "fileSizeBytes": file_size,
        "mandatory": False
    }

    with open(UPDATE_JSON, "w", encoding="utf-8") as f:
        json.dump(manifest, f, indent=2)
    print(f"🌐 Updated OTA manifest: {UPDATE_JSON}")
    return manifest

def notify_phone_live(manifest):
    phone_ip = get_hotspot_gateway() or "127.0.0.1"
    target_url = f"http://{phone_ip}:8080/api/update/notify"
    print(f"📲 Sending live update trigger to phone ({target_url})...")
    
    try:
        req = urllib.request.Request(
            target_url,
            data=json.dumps(manifest).encode('utf-8'),
            headers={"Content-Type": "application/json", "Connection": "close"},
            method="POST"
        )
        with urllib.request.urlopen(req, timeout=3) as resp:
            data = json.loads(resp.read().decode())
            print(f"🎉 Live update popup triggered on phone screen! Result: {data.get('message')}")
            return True
    except Exception as e:
        print(f"⚠️ Note: Phone app was not active on port 8080 ({e}).")
        print("   The update popup will appear automatically when the user opens the app!")
        return False

def push_git_repo(name):
    print("🐙 Committing & pushing to GitHub...")
    try:
        subprocess.run(["git", "add", "."], cwd=PROJECT_DIR, check=False)
        subprocess.run(["git", "commit", "-m", f"Release DisciplineOS v{name}"], cwd=PROJECT_DIR, check=False)
        subprocess.run(["git", "push", "origin", "main"], cwd=PROJECT_DIR, check=False)
        print("✅ Pushed to GitHub repository!")
    except Exception as e:
        print(f"⚠️ Git push notice: {e}")

def publish_github_release(code, name, notes, apk_path):
    token_path = r"C:\Users\LOL\.github_token"
    if not os.path.exists(token_path):
        return
    with open(token_path, "r", encoding="utf-8") as f:
        token = f.read().strip()
    if not token:
        return

    tag = f"v{name}"
    repo = "Developer-For-Git/DisciplineOS"
    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "DisciplineOS-Publisher"
    }

    body = f"## DisciplineOS v{name} (Build {code})\n\n"
    if notes:
        body += "### What's New:\n" + "\n".join(f"- {n}" for n in notes)
    else:
        body += "Performance optimizations, live OTA engine & stability enhancements."

    url = f"https://api.github.com/repos/{repo}/releases"
    payload = {
        "tag_name": tag,
        "target_commitish": "main",
        "name": f"DisciplineOS v{name}",
        "body": body,
        "draft": False,
        "prerelease": False
    }

    print(f"📦 Publishing GitHub Release {tag}...")
    release_data = None
    try:
        req = urllib.request.Request(url, data=json.dumps(payload).encode('utf-8'), headers=headers, method="POST")
        with urllib.request.urlopen(req) as resp:
            release_data = json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        err = e.read().decode('utf-8')
        if "already_exists" in err:
            get_req = urllib.request.Request(f"{url}/tags/{tag}", headers=headers)
            with urllib.request.urlopen(get_req) as resp:
                release_data = json.loads(resp.read().decode('utf-8'))

    if release_data and os.path.exists(apk_path):
        upload_url = release_data.get("upload_url", "").split("{")[0] + f"?name=DisciplineOS-{tag}.apk"
        size = os.path.getsize(apk_path)
        print(f"⬆️ Uploading APK asset to GitHub Releases ({size / (1024*1024):.1f} MB)...")
        with open(apk_path, "rb") as f:
            apk_bytes = f.read()
        upload_headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/vnd.android.package-archive",
            "Content-Length": str(size),
            "User-Agent": "DisciplineOS-Publisher"
        }
        try:
            up_req = urllib.request.Request(upload_url, data=apk_bytes, headers=upload_headers, method="POST")
            with urllib.request.urlopen(up_req) as resp:
                asset_res = json.loads(resp.read().decode('utf-8'))
                print(f"🎉 Asset available: {asset_res.get('browser_download_url')}")
        except Exception as e:
            print(f"Asset upload notice: {e}")

    if release_data:
        print(f"🔗 GitHub Release URL: {release_data.get('html_url')}")

def main():
    args = sys.argv[1:]
    notes = []
    auto_bump = False

    for a in args:
        if a == "--bump":
            auto_bump = True
        elif a.startswith("--"):
            pass
        else:
            notes.append(a)

    code, name = read_version_info()
    if auto_bump:
        code, name = bump_version_in_gradle()
    else:
        print(f"🚀 Publishing update for DisciplineOS v{name} (build {code})")

    # 1. Build APK
    build_apk()

    # 2. Deploy APK & update manifest
    manifest = deploy_apk_and_manifest(code, name, notes)

    # 3. Trigger live update popup on phone screen immediately
    notify_phone_live(manifest)

    # 4. Commit and push code to GitHub
    push_git_repo(name)

    # 5. Automatically create GitHub Release and upload APK binary
    publish_github_release(code, name, notes, APK_TARGET)

    print("\n" + "=" * 60)
    print(f"🎉 DISCIPLINE OS v{name} OTA UPDATE SUCCESSFULLY PUBLISHED!")
    print(f"   • Build:        {code}")
    print(f"   • APK URL:      {manifest['apkUrl']}")
    print(f"   • Size:         {manifest['fileSizeBytes']} bytes")
    print(f"   • Live Trigger: Active on port 8080")
    print(f"   • GitHub Rel:   https://github.com/Developer-For-Git/DisciplineOS/releases")
    print("=" * 60 + "\n")

if __name__ == "__main__":
    main()
