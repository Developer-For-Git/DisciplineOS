import sys
import os
import json
import urllib.request
import urllib.error
import socket
import subprocess
import re

# Ensure UTF-8 output on Windows
sys.stdout.reconfigure(encoding='utf-8')

DEFAULT_PORT = 8080

def get_hotspot_gateway():
    """Find default gateway IP (phone IP when PC is connected to mobile hotspot)"""
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

def check_port_open(ip, port=DEFAULT_PORT, timeout=0.5):
    """Fast socket test to verify port 8080 is listening"""
    try:
        with socket.create_connection((ip, port), timeout=timeout):
            return True
    except Exception:
        return False

def resolve_phone_url(target_override=None):
    """
    Auto-detect the phone URL:
    1. CLI override or ENV variable
    2. Hotspot Gateway (physical phone)
    3. Localhost (ADB forwarded / Emulator)
    """
    if target_override:
        if not target_override.startswith("http"):
            target_override = f"http://{target_override}:{DEFAULT_PORT}"
        return target_override

    env_ip = os.environ.get("DISCIPLINE_PHONE_IP")
    if env_ip:
        if not env_ip.startswith("http"):
            env_ip = f"http://{env_ip}:{DEFAULT_PORT}"
        return env_ip

    # Check Hotspot Gateway first (physical phone)
    gw = get_hotspot_gateway()
    if gw and check_port_open(gw, DEFAULT_PORT, timeout=1.2):
        return f"http://{gw}:{DEFAULT_PORT}"

    # Check Localhost (USB ADB / Emulator)
    if check_port_open("127.0.0.1", DEFAULT_PORT, timeout=0.5):
        return f"http://127.0.0.1:{DEFAULT_PORT}"

    # If neither is responding, fall back to gateway if found, else localhost
    if gw:
        return f"http://{gw}:{DEFAULT_PORT}"
    return f"http://127.0.0.1:{DEFAULT_PORT}"

def http_request(method, endpoint, data=None, base_url=None):
    if not base_url:
        base_url = resolve_phone_url()
    
    url = f"{base_url.rstrip('/')}{endpoint}"
    payload = json.dumps(data).encode('utf-8') if data is not None else None
    
    headers = {
        "User-Agent": "DisciplineSyncCLI/1.0",
        "Connection": "close"
    }
    if data is not None:
        headers["Content-Type"] = "application/json; charset=utf-8"

    req = urllib.request.Request(url, data=payload, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            raw = resp.read().decode('utf-8')
            return json.loads(raw) if raw else {}
    except urllib.error.URLError as e:
        raise ConnectionError(f"Failed to connect to DisciplineOS on {url}: {e.reason}")

def normalize_time(time_str):
    """Convert various time formats like 8:00pm, 20:00, 8:00 to HH:mm (24-hour)"""
    if not time_str:
        return ""
    t = time_str.strip().lower()
    is_pm = "pm" in t or "p.m." in t
    is_am = "am" in t or "a.m." in t
    t = re.sub(r"[^\d:]", "", t)
    
    parts = t.split(":")
    if not parts or not parts[0].isdigit():
        return time_str
    
    h = int(parts[0])
    m = int(parts[1]) if len(parts) > 1 and parts[1].isdigit() else 0
    
    if is_pm and h < 12:
        h += 12
    elif is_am and h == 12:
        h = 0
        
    return f"{h:02d}:{m:02d}"

# ==================== CLI ACTIONS ====================

def ping(base_url=None):
    url = base_url or resolve_phone_url()
    try:
        res = http_request("GET", "/api/status", base_url=url)
        print(f"✅ CONNECTED to DisciplineOS on {url}")
        print(f"   Tasks: {res.get('total_tasks')} | Completed: {res.get('completed_count')} ({res.get('percentage')}%)")
        print(f"   P1 Critical: {res.get('p1_critical_completed')}/{res.get('p1_critical_total')}")
        return True
    except Exception as e:
        print(f"❌ OFFLINE: Unable to reach DisciplineOS on {url}")
        print(f"   Reason: {e}")
        print("   Make sure:")
        print("   1. DisciplineOS app is opened on your phone.")
        print("   2. Hotspot / Wi-Fi or USB connection is active.")
        return False

def show_status(base_url=None):
    url = base_url or resolve_phone_url()
    try:
        status = http_request("GET", "/api/status", base_url=url)
        analytics = http_request("GET", "/api/analytics", base_url=url)
        tasks = http_request("GET", "/api/tasks", base_url=url)

        print("\n==========================================")
        print(f"   📱 DISCIPLINE OS ({url})")
        print("==========================================")
        print(f"Total Habits:        {status.get('total_tasks')}")
        print(f"Completed Today:     {status.get('completed_count')}")
        print(f"Completion Score:    {status.get('percentage')}%")
        print(f"Critical P1 Done:    {status.get('p1_critical_completed')} / {status.get('p1_critical_total')}")
        print(f"YT Video Queue:      {status.get('videos_pending', 0)} Pending • {status.get('videos_watched', 0)} Watched")
        print("------------------------------------------")
        for t in tasks:
            box = "[X]" if t['isCompleted'] else "[ ]"
            p_badge = t.get('priorityLabel', 'P2')
            time = f"({t['scheduledTime']})" if t.get('scheduledTime') else ""
            subs = t.get('subtasks', [])
            sub_info = f"[{len(subs)} subtasks]" if subs else ""
            print(f" #{t['id']} {box} {p_badge} | {t['title']} {time} {sub_info}")
        print("==========================================\n")
    except Exception as e:
        print(f"Error connecting: {e}")

def list_tasks(base_url=None):
    url = base_url or resolve_phone_url()
    try:
        tasks = http_request("GET", "/api/tasks", base_url=url)
        print(f"\nConnected to: {url}")
        print("=" * 80)
        print(" ID | STATUS | PRIORITY | CATEGORY   | TIME  | TITLE")
        print("-" * 80)
        for t in tasks:
            status = "DONE" if t['isCompleted'] else "TODO"
            p = f"P{t.get('priority', 2)}"
            cat = t.get('category', 'Habit')[:10].ljust(10)
            time = (t.get('scheduledTime') or "--:--").ljust(5)
            print(f" {str(t['id']).rjust(2)} | {status.ljust(6)} | {p.ljust(8)} | {cat} | {time} | {t['title']}")
        print("=" * 80 + "\n")
    except Exception as e:
        print(f"Error listing tasks: {e}")

def add_task(title, scheduled_time="", priority=2, category="Habit", description="", ring_sound=False, base_url=None):
    url = base_url or resolve_phone_url()
    norm_time = normalize_time(scheduled_time)
    payload = {
        "title": title,
        "description": description,
        "category": category,
        "priority": priority,
        "scheduledTime": norm_time,
        "ringSound": ring_sound
    }
    try:
        res = http_request("POST", "/api/task/add", payload, base_url=url)
        task = res.get("task", {})
        print(f"✅ Added Task #{task.get('id', '?')} on {url}:")
        print(f"   Title:    {task.get('title', title)}")
        print(f"   Time:     {task.get('scheduledTime', norm_time)} ({task.get('displayTime', '')})")
        print(f"   Priority: P{task.get('priority', priority)}")
        print(f"   Category: {task.get('category', category)}")
        return task
    except Exception as e:
        print(f"❌ Failed to add task: {e}")
        return None

def add_tasks_batch(tasks_list, base_url=None):
    url = base_url or resolve_phone_url()
    print(f"Syncing {len(tasks_list)} tasks to {url}...")
    success_count = 0
    for t in tasks_list:
        title = t.get("title", "Untitled")
        time_str = normalize_time(t.get("scheduledTime", t.get("time", "")))
        pri = int(t.get("priority", 2))
        cat = t.get("category", "Habit")
        desc = t.get("description", "")
        sound = bool(t.get("ringSound", False))
        
        res = add_task(title, scheduled_time=time_str, priority=pri, category=cat, description=desc, ring_sound=sound, base_url=url)
        if res:
            success_count += 1
    print(f"\n🎉 Successfully added {success_count}/{len(tasks_list)} tasks to phone!")

# ==================== MAIN DISPATCHER ====================

if __name__ == "__main__":
    args = sys.argv[1:]
    
    # Check for custom base_url or IP in args
    custom_url = None
    filtered_args = []
    for a in args:
        if a.startswith("--ip="):
            custom_url = a.split("=", 1)[1]
        elif a.startswith("--url="):
            custom_url = a.split("=", 1)[1]
        elif a == "--emulator":
            custom_url = "http://127.0.0.1:8080"
        elif a == "--phone":
            gw = get_hotspot_gateway()
            if gw: custom_url = f"http://{gw}:8080"
        else:
            filtered_args.append(a)
            
    args = filtered_args
    cmd = args[0].lower() if args else "ping"

    if cmd in ("ping", "check", "test"):
        ping(custom_url)

    elif cmd in ("status", "info"):
        show_status(custom_url)

    elif cmd in ("list", "ls", "tasks"):
        list_tasks(custom_url)

    elif cmd == "add":
        # Supports:
        # python sync_phone.py add "Title" 20:00 [priority] [category]
        # python sync_phone.py add "Title" time="20:00" priority=1 category="Coding"
        if len(args) < 2:
            print("Usage: python sync_phone.py add <Title> [time] [priority] [category]")
            sys.exit(1)
            
        title = args[1]
        time_val = ""
        priority = 2
        category = "Habit"
        desc = ""
        sound = False

        idx = 2
        while idx < len(args):
            arg = args[idx]
            if "=" in arg:
                k, v = arg.split("=", 1)
                k = k.lower()
                if k in ("time", "scheduledtime"): time_val = v
                elif k in ("pri", "priority"): priority = int(v)
                elif k in ("cat", "category"): category = v
                elif k in ("desc", "description"): desc = v
                elif k in ("sound", "ringsound"): sound = v.lower() == "true"
            else:
                # Positional parsing
                if idx == 2:
                    time_val = arg
                elif idx == 3 and arg.isdigit():
                    priority = int(arg)
                elif idx == 4:
                    category = arg
            idx += 1
            
        add_task(title, scheduled_time=time_val, priority=priority, category=category, description=desc, ring_sound=sound, base_url=custom_url)

    elif cmd == "add-batch":
        # Accepts JSON string or filename
        if len(args) < 2:
            print("Usage: python sync_phone.py add-batch '[{\"title\": \"...\"}]'")
            sys.exit(1)
        raw = " ".join(args[1:])
        tasks = []
        if os.path.exists(raw):
            with open(raw, "r", encoding="utf-8") as f:
                tasks = json.load(f)
        else:
            tasks = json.loads(raw)
        add_tasks_batch(tasks, custom_url)

    elif cmd == "complete" and len(args) >= 2:
        task_id = int(args[1])
        res = http_request("POST", "/api/task/toggle", {"id": task_id, "completed": True}, base_url=custom_url)
        print(f"Task #{task_id} COMPLETED ✅")

    elif cmd == "uncomplete" and len(args) >= 2:
        task_id = int(args[1])
        res = http_request("POST", "/api/task/toggle", {"id": task_id, "completed": False}, base_url=custom_url)
        print(f"Task #{task_id} marked TODO ⬜")

    elif cmd == "delete" and len(args) >= 2:
        task_id = int(args[1])
        res = http_request("DELETE", f"/api/task?id={task_id}", base_url=custom_url)
        print(f"Task #{task_id} DELETED 🗑️")

    elif cmd == "vibrate":
        http_request("POST", "/api/command", {"action": "TRIGGER_VIBRATION"}, base_url=custom_url)
        print("⚡ Triggered rapid vibration on phone")

    elif cmd == "backup":
        filepath = args[1] if len(args) > 1 else "discipline_backup.json"
        data = http_request("GET", "/api/backup", base_url=custom_url)
        with open(filepath, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
        print(f"✅ Database backed up to {filepath}")

    elif cmd == "restore":
        filepath = args[1] if len(args) > 1 else "discipline_backup.json"
        with open(filepath, "r", encoding="utf-8") as f:
            data = json.load(f)
        res = http_request("POST", "/api/restore", data, base_url=custom_url)
        print("✅ Database restored:", res.get("message"))

    else:
        print("DisciplineOS Unified Sync Bridge:")
        print("  python sync_phone.py ping                           (Test connection to phone)")
        print("  python sync_phone.py list                           (Show all tasks on phone)")
        print("  python sync_phone.py add \"Title\" 20:00 [priority]   (Add single task)")
        print("  python sync_phone.py add-batch '[{...}, {...}]'     (Add multiple tasks at once)")
        print("  python sync_phone.py complete <id>                  (Mark task done)")
        print("  python sync_phone.py delete <id>                    (Delete task)")
        print("  python sync_phone.py backup                         (Backup phone database)")
        print("  python sync_phone.py restore                        (Restore phone database)")
