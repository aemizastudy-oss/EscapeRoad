#!/usr/bin/env python3
"""Drive the Pleos emulator like a real trip, so the app can be tested on live data.

Nothing is scripted in the app. This tool only plays the *car and the driver*:
  * gear and speed go through the Pleos Virtual Console (real Vehicle SDK signals),
  * position goes into the emulator GPS (Pleos FusedLocation / LocationManager),
  * each leg is routed live from wherever the car is now (OSRM / OpenStreetMap),
    optionally through a random detour, with varying speed and red-light stops,
  * before each leg it waits in P until the player presses 출발하기 in the app,
    then shifts to D; after arriving it parks (gear P), which brings the app back.
A live map is served at http://127.0.0.1:8765 to watch the car and the app state.

  python3 tools/drive_sim.py                     # 40 km/h average, x10 time
  python3 tools/drive_sim.py --fast 20 --detour 0.5
  python3 tools/drive_sim.py --manual            # press Enter (instead of watching 출발하기) to depart
  python3 tools/drive_sim.py --start 37.2655,127.0002   # starting point (default: Suwon station)

Requires: a running Pleos emulator with the debug app installed and a case started in the app.
"""
import argparse, json, math, os, random, re, shutil, subprocess, sys, threading, time, urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
CATALOG = os.path.join(os.path.dirname(HERE), "app", "src", "main", "java", "com", "team", "carescape", "game", "TourCatalog.kt")
VC = "http://127.0.0.1:8088"
SERVICE = "com.team.carescape/.drive.DriveService"

state = {"car": None, "route": [], "stops": [], "app": {}, "log": [], "speed": 0, "gear": "Park"}


def log(msg):
    stamp = time.strftime("%H:%M:%S")
    print(f"[{stamp}] {msg}", flush=True)
    state["log"] = (state["log"] + [f"{stamp} {msg}"])[-12:]


def load_places():
    """Places straight from TourCatalog.kt so the tool never drifts from the app."""
    text = open(CATALOG, encoding="utf-8").read()
    rows = re.findall(r'WaypointConfig\("([^"]+)",\s*"([^"]+)",\s*([0-9.]+),\s*([0-9.]+)', text)
    return [{"id": i, "title": n, "latitude": float(a), "longitude": float(b), "arrivalRadiusM": 500} for i, n, a, b in rows]


def haversine(a, b):
    r = 6371000.0
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    h = math.sin((p2 - p1) / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(math.radians(b[1] - a[1]) / 2) ** 2
    return 2 * r * math.asin(math.sqrt(min(1.0, h)))


def find_adb():
    sdk = os.environ.get("ANDROID_HOME") or os.path.expanduser("~/Library/Android/sdk")
    for c in (os.path.join(sdk, "platform-tools", "adb"), shutil.which("adb")):
        if c and os.path.exists(c):
            return c
    sys.exit("adb를 찾을 수 없습니다.")


class Car:
    def __init__(self, adb, serial):
        self.adb, self.serial = adb, serial
        subprocess.run([adb, "-s", serial, "forward", "tcp:8088", "tcp:8088"], check=False, capture_output=True)
        threading.Thread(target=self._heartbeat, daemon=True).start()
        time.sleep(1.5)

    def _heartbeat(self):  # the Virtual Console only accepts writes while a client is attached
        while True:
            self._http("POST", "/browser/session?client=drive-sim")
            time.sleep(1)

    def _http(self, method, path):
        try:
            req = urllib.request.Request(VC + path, method=method)
            with urllib.request.urlopen(req, timeout=3) as r:
                return r.read().decode()
        except Exception as e:
            return f"error {e}"

    def gear(self, g):
        state["gear"] = g
        out = self._http("PUT", f"/vehicle/gear?gear={g}")
        if "error" in out:
            log(f"기어 {g} 설정 실패: {out[:80]}")

    def speed(self, kph):
        state["speed"] = round(kph)
        self._http("PUT", f"/vehicle/controls?speed={kph:.0f}&accelerator={'10' if kph > 0 else '0'}&brake={'0' if kph > 0 else '20'}&steering=0")

    def fix(self, lat, lon):
        state["car"] = [lat, lon]
        subprocess.run([self.adb, "-s", self.serial, "emu", "geo", "fix", f"{lon:.6f}", f"{lat:.6f}"],
                       check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

    def app(self):
        out = subprocess.run([self.adb, "-s", self.serial, "shell", "dumpsys", "activity", "service", SERVICE],
                             capture_output=True, text=True).stdout
        info = dict(re.findall(r"(\w+)=([^\s]+)", out))
        last = re.search(r"lastLine=(.*)", out)
        info["lastLine"] = last.group(1).strip() if last else ""
        state["app"] = info
        return info


def osrm(points):
    coords = ";".join(f"{lon:.6f},{lat:.6f}" for lat, lon in points)
    url = f"https://router.project-osrm.org/route/v1/driving/{coords}?overview=full&geometries=geojson"
    # curl uses the system certificate store (python.org builds on macOS often lack one).
    out = subprocess.run(["curl", "-s", "-m", "15", "-A", "route-sim-dev/0.1", url], capture_output=True, text=True)
    data = json.loads(out.stdout or "{}")
    if data.get("code") != "Ok":
        raise RuntimeError(data.get("code"))
    return [(lat, lon) for lon, lat in data["routes"][0]["geometry"]["coordinates"]]


def plan_leg(here, stop, detour):
    via = []
    if random.random() < detour:
        # A random via point beside the direct line: a different road than last time.
        mid = ((here[0] + stop[0]) / 2, (here[1] + stop[1]) / 2)
        d = haversine(here, stop)
        ang = random.uniform(0, 2 * math.pi)
        off = max(300, d * 0.35) / 111_000
        via = [(mid[0] + off * math.sin(ang), mid[1] + off * math.cos(ang) / math.cos(math.radians(mid[0])))]
        log(f"우회 경로 선택 (경유점 {via[0][0]:.4f},{via[0][1]:.4f})")
    try:
        return osrm([here] + via + [stop])
    except Exception as e:
        log(f"OSRM 실패({e}) → 직선 경로")
        return [here, stop]


def drive(car, path, args):
    cum = [0.0]
    for a, b in zip(path, path[1:]):
        cum.append(cum[-1] + haversine(a, b))
    state["route"] = path
    total, dist = cum[-1], 0.0
    cruise = args.speed
    next_light = random.uniform(400, 1200)
    car.gear("Drive")
    while dist < total:
        if dist >= next_light:  # red light: stop in D, BGM keeps playing
            car.speed(0)
            wait = random.uniform(3, 8) / max(1.0, args.fast / 5)
            time.sleep(wait)
            next_light = dist + random.uniform(500, 1500)
        cruise = max(15, min(70, cruise + random.uniform(-6, 6)))
        car.speed(cruise)
        dist = min(total, dist + cruise / 3.6 * args.fast)
        k = next(i for i in range(len(cum) - 1) if cum[i + 1] >= dist) if dist < total else len(cum) - 2
        f = (dist - cum[k]) / ((cum[k + 1] - cum[k]) or 1)
        lat = path[k][0] + (path[k + 1][0] - path[k][0]) * f
        lon = path[k][1] + (path[k + 1][1] - path[k][1]) * f
        car.fix(lat, lon)
        time.sleep(1)
    car.speed(0)
    return path[-1]


def wait_departure(car, index, place, args):
    """Wait in P until the player presses 출발하기 in the app for this chapter (service: armed=true)."""
    car.gear("Park")
    if args.manual:
        input(f"  ▶ 앱에서 {place} 출발하기를 누른 뒤 Enter를 누르면 출발합니다... ")
        return
    log(f"앱에서 '{place}' 출발하기를 누르면 출발합니다 (기어 P 대기)")
    start, last = time.time(), 0
    while True:
        info = car.app()
        if info.get("trip") == "EN_ROUTE" and info.get("chapter") == str(index) and info.get("armed") == "true":
            if time.time() - start >= args.dwell:
                log("출발하기 확인 → 기어 D")
                return
        if time.time() - last >= 20:
            last = time.time()
            log(f"  대기 중: trip={info.get('trip')} chapter={info.get('chapter')} armed={info.get('armed')}")
        time.sleep(1)


PAGE = """<!doctype html><html><head><meta charset=utf-8><title>Drive Sim</title>
<link rel=stylesheet href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>body{margin:0;font-family:system-ui;background:#1b0b0e;color:#efe5cc}#map{height:72vh}#info{padding:10px 16px;font-size:14px;line-height:1.5}
b{color:#c8a05c}.log{color:#bda988;font-size:12px;white-space:pre-wrap}</style></head><body><div id=map></div><div id=info></div>
<script>
const map=L.map('map').setView([37.285,127.03],13);L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{attribution:'© OpenStreetMap'}).addTo(map);
let car=L.circleMarker([0,0],{radius:9,color:'#fff',fillColor:'#e04b3b',fillOpacity:1}).addTo(map),route=L.polyline([],{color:'#3b7be0',weight:5}).addTo(map),stops=[],follow=true;
map.on('dragstart',()=>follow=false);
async function tick(){const s=await (await fetch('/state')).json();
 if(!stops.length&&s.stops.length){s.stops.forEach((p,i)=>{stops.push(L.circle([p.lat,p.lon],{radius:p.r,color:'#c8a05c'}).addTo(map).bindTooltip((i+1)+'. '+p.title,{permanent:true}))})}
 route.setLatLngs(s.route);if(s.car){car.setLatLng(s.car);if(follow)map.panTo(s.car)}
 const a=s.app;document.getElementById('info').innerHTML=`<b>앱</b> trip=${a.trip||'-'} chapter=${a.chapter||'-'} leg=${a.leg||'-'} 남은거리=${a.remaining||'-'}m (내비 ${a.nav||'-'}) · <b>차</b> 기어 ${s.gear} · ${s.speed} km/h<br><b>마지막 대사</b> ${a.lastLine||''}<div class=log>${s.log.join('\\n')}</div>`}
setInterval(tick,1000);tick();</script></body></html>"""


class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        body = json.dumps(state).encode() if self.path == "/state" else PAGE.encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json" if self.path == "/state" else "text/html; charset=utf-8")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *a):
        pass


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL", "emulator-5554"))
    ap.add_argument("--speed", type=float, default=40.0, help="average km/h")
    ap.add_argument("--fast", type=float, default=10.0, help="time compression")
    ap.add_argument("--detour", type=float, default=0.3, help="chance (0-1) of a detour on each leg")
    ap.add_argument("--dwell", type=float, default=2.0, help="seconds between 출발하기 and shifting to D")
    ap.add_argument("--manual", action="store_true", help="press Enter instead of waiting for 출발하기")
    ap.add_argument("--start", default="37.265506,127.000215", help="lat,lon of the starting point")
    ap.add_argument("--from-stop", type=int, default=1, help="first stop number to drive to (1-based)")
    ap.add_argument("--seed", type=int, default=None)
    ap.add_argument("--port", type=int, default=8765)
    args = ap.parse_args()
    random.seed(args.seed)

    stops = load_places()
    state["stops"] = [{"lat": s["latitude"], "lon": s["longitude"], "r": s.get("arrivalRadiusM", 500), "title": s["title"]} for s in stops]
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    log(f"실시간 지도: http://127.0.0.1:{args.port}")

    car = Car(find_adb(), args.serial)
    here = tuple(float(v) for v in args.start.split(","))
    car.gear("Park"); car.speed(0); car.fix(*here)
    info = car.app()
    if not info.get("trip"):
        log("주행 중 안내 서비스가 실행 중이 아닙니다. 앱에서 사건을 시작했는지 확인하세요.")
    time.sleep(2)
    for i in range(args.from_stop - 1, len(stops)):
        stop = stops[i]
        target = (stop["latitude"], stop["longitude"])
        wait_departure(car, i, stop["title"], args)
        log(f"CH.{i + 1} {stop['title']}(으)로 출발 · 현재 위치에서 실시간 경로 탐색")
        here = drive(car, plan_leg(here, target, args.detour), args)
        info = car.app()
        car.gear("Park")
        log(f"{stop['title']} 도착 · 기어 P (앱이 다시 열립니다) · 서비스 awaitingStop={info.get('awaitingStop')}")
    car.gear("Park")
    log("모든 경유지 주행 완료")
    time.sleep(3)


if __name__ == "__main__":
    main()
