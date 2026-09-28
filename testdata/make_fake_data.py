"""
Makes all the fake data in this repo from one seeded set of made-up shifts:

  demo/demo-backup.json        ~5 months of shifts; restore it in the app (Settings > Restore from backup)
  testdata/photos/fake_NN.jpg  rendered cashout slips (OcrAccuracyTest runs real OCR over these)
  testdata/ground_truth.json   the answer key for those slips
  testdata/ocr_dump.json       simulated OCR output for those slips (ParserAccuracyTest replays it)

The OCR dump is simulated, not real ML Kit output: each printed row is split into column fragments
with their own tilted corners, plus a few typical misreads, so RowBuilder still has to stitch rows.
Running OcrAccuracyTest on a device replaces it with the real thing.

Needs Pillow. Run from the repo root:  python testdata/make_fake_data.py
"""
import json
import math
import random
import uuid
from datetime import date, datetime, timedelta
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
rng = random.Random(7)
SERVER = "Alex"
COWORKERS = ["Sam", "Jordan", "Riley", "Casey"]

# Average tips per shift by weekday (Mon..Sun) and start time: busy weekends, a good Sunday brunch.
DAY_WEIGHT = [0.75, 0.8, 0.9, 1.0, 1.35, 1.45, 1.1]
STARTS = {"lunch": ("11:00", 0.7), "dinner": ("16:30", 1.15), "late": ("19:30", 0.95)}


def r2(v):
    return round(v + 1e-9, 2)


def make_shift(d: date, n: int, trend: float):
    part = rng.choices(list(STARTS), weights=[3, 6, 2] if d.weekday() < 6 else [5, 4, 1])[0]
    start, part_weight = STARTS[part]
    hours = rng.choice([4.5, 5, 5.5, 6, 6.5, 7, 8]) if part != "late" else rng.choice([5, 5.5, 6])
    sales = r2(rng.gauss(900, 140) * DAY_WEIGHT[d.weekday()] * part_weight * trend * hours / 6)
    # Tip rate slips a little on the busiest nights.
    rate = rng.gauss(0.165, 0.015) - (0.012 if sales > 1300 else 0)
    tips = r2(sales * rate)
    lwb = r2(round(sales * rng.uniform(0.05, 0.22) / 0.5) * 0.5)
    food = r2(sales - lwb)
    covers = max(4, round(sales / rng.uniform(38, 52)))
    open_t = datetime.combine(d, datetime.strptime(start, "%H:%M").time()) + timedelta(minutes=rng.randint(-20, 25))
    close_t = open_t + timedelta(hours=hours, minutes=rng.randint(-15, 15))

    payment_total = r2(sales * 1.05 + tips)
    tenders = rng.sample(["DEBITC", "VISA", "MASTCH", "AMEX", "RDM CA", "QSA", "BIRTHD"], rng.randint(2, 4))
    weights = [rng.uniform(0.5, 2) for _ in tenders]
    cash = r2(payment_total * rng.uniform(0, 0.15)) if rng.random() < 0.6 else 0.0
    rest = payment_total - cash
    amounts = [r2(rest * w / sum(weights)) for w in weights]
    amounts[-1] = r2(rest - sum(amounts[:-1]))
    payments = [{"type": "CASH", "amount": cash, "count": 1 if cash else 0}] + [
        {"type": t, "amount": a, "count": max(1, round(a / 120))} for t, a in zip(tenders, amounts)
    ]
    out = [{"name": rng.choice(COWORKERS), "amount": r2(sales * 0.03)}] if rng.random() < 0.3 else []
    return {
        "id": str(uuid.UUID(int=rng.getrandbits(128))),
        "serverName": SERVER,
        "date": d.isoformat(),
        "openTime": open_t.strftime("%H:%M"),
        "closeTime": close_t.strftime("%H:%M"),
        "closeAt": close_t,
        "sales": sales,
        "tips": tips,
        # Cash take-home after tip-out, entered on about half the shifts.
        "takeHome": r2(tips - sales * 0.03) if rng.random() < 0.5 else None,
        "foodSales": food,
        "foodVolume": max(1, round(food / 18)),
        "lwbSales": lwb,
        "lwbVolume": max(1, round(lwb / 9)),
        "covers": covers,
        "avgCheck": r2(sales / covers),
        "checks": max(1, round(covers / 2.6)),
        "staffChecks": rng.choice([0, 0, 0, 1]),
        "paymentTotal": payment_total,
        "net": r2(payment_total - tips),
        "payments": payments,
        "transfersOut": out,
        "transfersIn": [],
        "reference": n,
        "source": "manual",
        "createdAt": int(datetime.combine(d, datetime.min.time()).timestamp() * 1000),
    }


def shifts():
    out, d, n = [], date(2026, 5, 1), 1
    while d <= date(2026, 9, 26):
        # About four shifts a week, weekends more likely; earnings creep up over the summer.
        if rng.random() < [0.45, 0.4, 0.5, 0.55, 0.8, 0.85, 0.6][d.weekday()]:
            out.append(make_shift(d, n, 1 + (d - date(2026, 5, 1)).days / 700))
            n += 1
        d += timedelta(days=1)
    return out


# ---- Receipt text -----------------------------------------------------------------------------

def time12(t: datetime):
    return t.strftime("%I:%M%p").lstrip("0")


def mdy(d):
    return f"{d.month}/{d.day:02d}/{d.year % 100:02d}"


def money(v):
    return f"{v:.2f}"


def receipt_rows(s):
    """The slip as printed, one string per row. Two or more spaces separate columns."""
    d = date.fromisoformat(s["date"])
    open_t = datetime.combine(d, datetime.strptime(s["openTime"], "%H:%M").time())
    close_t = s["closeAt"]
    rows = [
        "*" * 40,
        f"*******  CASHOUT FOR {SERVER}  *******",
        f"*******  {mdy(close_t)} {time12(close_t)}  *******",
        f"  BUSINESS DAY: {d.strftime('%A').upper()} {mdy(d)}",
        f"  OPEN: {time12(open_t)}  CLOSE: {time12(close_t)}",
        "*" * 40,
        f"REFERENCE # {s['reference']}",
        "",
        "PAYMENTS      TIPS      NET      #",
    ]
    for p in s["payments"]:
        label = p["type"] + ("$" if len(p["type"]) >= 6 else " $")
        if p["type"] == "CASH":
            rows.append(f"{label}  {money(p['amount'])} - {money(s['tips'])}= {money(p['amount'] - s['tips'])}  {p['count']}")
        else:
            rows.append(f"{label}  {money(p['amount'])}  {p['count']}")
    count = sum(p["count"] for p in s["payments"])
    rows += [
        f"TOTAL $  {money(s['paymentTotal'])} - {money(s['tips'])}= {money(s['net'])}  {count}",
        "",
        "ITEMS      DOLLARS      VOLUME",
        f"FOOD $  {money(s['foodSales'])}  {s['foodVolume']}",
        f"L/W/B  {money(s['lwbSales'])}  {s['lwbVolume']}",
        "SERVER",
        f"SALES $  {money(s['sales'])}",
        f"# COVERS: {s['covers']}",
        f"AVG CHECK$ {money(s['avgCheck'])}",
        f"TOTAL # OF CHECKS: {s['checks']}",
        f"TOTAL # OF STAFF CHECKS: {s['staffChecks']}",
    ]
    if s["transfersOut"]:
        rows.append("TRANSFERS OUT")
        rows += [f"{t['name']}  {money(t['amount'])}" for t in s["transfersOut"]]
        rows.append(f"TOTAL  {money(sum(t['amount'] for t in s['transfersOut']))}")
    rows.append("*" * 40)
    return rows


# Misreads seen on real slips: stray k in rules, $ read as S, O as 0, W as N, OUT as QUT.
MISREADS = [("*****", "****k"), ("DEBITC$", "DEBITCS"), ("RDM CA$", "RDM CAS"), ("FOOD", "FO0D"), ("L/W/B", "L/N/B"), ("TRANSFERS OUT", "TRANSFERS QUT")]


def ocr_text(t):
    for a, b in MISREADS:
        if a in t and rng.random() < 0.3:
            t = t.replace(a, b, 1)
    return t


# ---- Rendering + simulated OCR ----------------------------------------------------------------

FONT = ImageFont.truetype("C:/Windows/Fonts/consola.ttf", 28) if Path("C:/Windows/Fonts/consola.ttf").exists() \
    else ImageFont.truetype("DejaVuSansMono.ttf", 28)
CW = FONT.getlength("M")
LH = 36
PAD = 40


def render(s, name):
    rows = receipt_rows(s)
    w, h = int(PAD * 2 + CW * 42), PAD * 2 + LH * len(rows)
    paper = Image.new("RGBA", (w, h), (246, 244, 238, 255))
    draw = ImageDraw.Draw(paper)
    fragments = []
    for i, row in enumerate(rows):
        y = PAD + i * LH
        draw.text((PAD, y), row, font=FONT, fill=(40, 40, 48))
        col = 0
        for part in row.split("  "):
            if part.strip():
                lead = len(part) - len(part.lstrip())
                x0 = PAD + (col + lead) * CW
                fragments.append((part.strip(), x0, y + 4, x0 + len(part.strip()) * CW, y + 30))
            col += len(part) + 2

    angle = rng.uniform(-4, 4)
    rotated = paper.rotate(angle, resample=Image.BICUBIC, expand=True)
    photo = Image.new("RGB", (rotated.width + 160, rotated.height + 200), (92, 84, 76))
    ox, oy = 80 + rng.randint(-30, 30), 100 + rng.randint(-30, 30)
    photo.paste(rotated, (ox, oy), rotated)
    photo = photo.filter(ImageFilter.GaussianBlur(0.6))
    photo.save(ROOT / "testdata/photos" / name, quality=85)

    # PIL rotates counter-clockwise about the centre; map fragment corners the same way.
    a = math.radians(angle)
    cx, cy = w / 2, h / 2
    rcx, rcy = ox + rotated.width / 2, oy + rotated.height / 2

    def pt(x, y):
        dx, dy = x - cx, y - cy
        return [round(rcx + dx * math.cos(a) + dy * math.sin(a), 1), round(rcy - dx * math.sin(a) + dy * math.cos(a), 1)]

    lines = [{"text": ocr_text(t), "corners": pt(x0, y0) + pt(x1, y0) + pt(x1, y1) + pt(x0, y1)} for t, x0, y0, x1, y1 in fragments]
    rng.shuffle(lines)  # OCR engines don't return lines in reading order
    return {"file": name, "rotation": 0, "lines": lines}


def truth(s, name):
    d = date.fromisoformat(s["date"])
    keys = ["openTime", "closeTime", "reference", "payments", "paymentTotal", "tips", "net", "foodSales", "foodVolume",
            "lwbSales", "lwbVolume", "sales", "covers", "avgCheck", "staffChecks", "transfersOut", "transfersIn"]
    return {"file": name, "serverName": SERVER, "businessDate": s["date"], "dayOfWeek": d.strftime("%A").upper(),
            "totalChecks": s["checks"], **{k: s[k] for k in keys}}


def main():
    all_shifts = shifts()
    (ROOT / "demo").mkdir(exist_ok=True)
    (ROOT / "testdata/photos").mkdir(parents=True, exist_ok=True)
    demo = [{k: v for k, v in s.items() if k != "closeAt"} for s in all_shifts]
    (ROOT / "demo/demo-backup.json").write_text(json.dumps({"version": 1, "shifts": demo}, indent=1))

    samples = rng.sample(all_shifts, 12)
    dumps, answers = [], []
    for i, s in enumerate(samples, 1):
        name = f"fake_{i:02d}.jpg"
        dumps.append(render(s, name))
        answers.append(truth(s, name))
    (ROOT / "testdata/ocr_dump.json").write_text(json.dumps(dumps))
    (ROOT / "testdata/ground_truth.json").write_text(json.dumps(answers, indent=1))
    print(f"{len(demo)} demo shifts, {len(samples)} fake slips")


if __name__ == "__main__":
    main()
