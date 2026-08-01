import struct
import sys

UPM = 1000
ASCENT = 928
DESCENT = -244
ADVANCE = 690

STEM = (131, 0, 220, 700)
TOPBAR = (131, 627, 562, 700)
CROSSBAR = (30, 158, 411, 233)

OUTER = [
    (200, 398, 1), (400, 398, 1),
    (646, 398, 0), (646, 199, 1),
    (646, 0, 0), (400, 0, 1),
    (200, 0, 1),
]
COUNTER = [
    (220, 73, 1), (400, 73, 1),
    (560, 73, 0), (560, 199, 1),
    (560, 325, 0), (400, 325, 1),
    (220, 325, 1),
]


def rect(x0, y0, x1, y1):
    return [(x0, y0, 1), (x0, y1, 1), (x1, y1, 1), (x1, y0, 1)]


CONTOURS = [rect(*STEM), rect(*TOPBAR), rect(*CROSSBAR), OUTER, COUNTER]


def ser_glyph(contours):
    pts = [p for c in contours for p in c]
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    xmin, ymin, xmax, ymax = min(xs), min(ys), max(xs), max(ys)
    data = struct.pack(">hhhhh", len(contours), xmin, ymin, xmax, ymax)
    end = -1
    for c in contours:
        end += len(c)
        data += struct.pack(">H", end)
    data += struct.pack(">H", 0)
    for p in pts:
        data += struct.pack(">B", 0x01 if p[2] else 0x00)
    prev = 0
    for x in xs:
        data += struct.pack(">h", x - prev)
        prev = x
    prev = 0
    for y in ys:
        data += struct.pack(">h", y - prev)
        prev = y
    if len(data) % 4:
        data += b"\x00" * (4 - len(data) % 4)
    return data, (xmin, ymin, xmax, ymax)


def checksum(data):
    if len(data) % 4:
        data += b"\x00" * (4 - len(data) % 4)
    return sum(struct.unpack(">%dI" % (len(data) // 4), data)) & 0xFFFFFFFF


def name_table():
    strings = {
        1: "Kosht Rubel",
        2: "Regular",
        3: "KoshtRubel-1.0",
        4: "Kosht Rubel",
        6: "KoshtRubel-Regular",
    }
    records = b""
    storage = b""
    for nid in sorted(strings):
        s = strings[nid].encode("utf-16-be")
        records += struct.pack(">HHHHHH", 3, 1, 0x0409, nid, len(s), len(storage))
        storage += s
    header = struct.pack(">HHH", 0, len(strings), 6 + 12 * len(strings))
    return header + records + storage


def cmap_table():
    seg = struct.pack(">HHHHHHH", 4, 32, 0, 4, 4, 1, 0)
    seg += struct.pack(">HH", 0x20BD, 0xFFFF)
    seg += struct.pack(">H", 0)
    seg += struct.pack(">HH", 0x20BD, 0xFFFF)
    seg += struct.pack(">HH", (1 - 0x20BD) & 0xFFFF, 1)
    seg += struct.pack(">HH", 0, 0)
    return struct.pack(">HHHHI", 0, 1, 3, 1, 12) + seg


def build():
    glyf, bbox = ser_glyph(CONTOURS)
    loca = struct.pack(">HHH", 0, 0, len(glyf) // 2)
    xmin, ymin, xmax, ymax = bbox

    head = struct.pack(
        ">IIIIHHqqhhhhHHhhh",
        0x00010000, 0x00010000, 0, 0x5F0F3CF5,
        3, UPM, 0, 0,
        xmin, ymin, xmax, ymax,
        0, 8, 2, 0, 0,
    )
    hhea = struct.pack(
        ">IhhhHhhhhhhhhhhhH",
        0x00010000, ASCENT, DESCENT, 0,
        ADVANCE, 0, ADVANCE - xmax, xmax,
        1, 0, 0, 0, 0, 0, 0, 0, 2,
    )
    maxp = struct.pack(
        ">IHHHHHHHHHHHHHH",
        0x00010000, 2, 26, 5, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0,
    )
    hmtx = struct.pack(">HhHh", 500, 0, ADVANCE, 30)
    post = struct.pack(">IihhIIIII", 0x00030000, 0, -100, 50, 0, 0, 0, 0, 0)
    os2 = struct.pack(
        ">HhHHHhhhhhhhhhhh",
        4, 550, 400, 5, 0,
        650, 700, 0, 140, 650, 700, 0, 480, 50, 260,
        0,
    )
    os2 += b"\x00" * 10
    os2 += struct.pack(">IIII", 0, 0, 0, 0)
    os2 += b"KSHT"
    os2 += struct.pack(
        ">HHHhhhHH", 0x0040, 0x20BD, 0x20BD, ASCENT, DESCENT, 0, ASCENT, -DESCENT,
    )
    os2 += struct.pack(">II", 0, 0)
    os2 += struct.pack(">hhHHH", 500, 700, 0, 0x20BD, 1)

    tables = {
        b"OS/2": os2,
        b"cmap": cmap_table(),
        b"glyf": glyf,
        b"head": head,
        b"hhea": hhea,
        b"hmtx": hmtx,
        b"loca": loca,
        b"maxp": maxp,
        b"name": name_table(),
        b"post": post,
    }

    tags = sorted(tables)
    num = len(tags)
    search = 16 * (2 ** (num.bit_length() - 1))
    header = struct.pack(
        ">IHHHH", 0x00010000, num,
        search, num.bit_length() - 1, num * 16 - search,
    )
    offset = 12 + 16 * num
    directory = b""
    body = b""
    head_offset = None
    for tag in tags:
        data = tables[tag]
        padded = data + b"\x00" * ((4 - len(data) % 4) % 4)
        if tag == b"head":
            head_offset = offset
        directory += struct.pack(">4sIII", tag, checksum(padded), offset, len(data))
        body += padded
        offset += len(padded)

    font = header + directory + body
    adjustment = (0xB1B0AFBA - checksum(font)) & 0xFFFFFFFF
    return font[: head_offset + 8] + struct.pack(">I", adjustment) + font[head_offset + 12 :]


font = build()
for out in sys.argv[1:]:
    with open(out, "wb") as f:
        f.write(font)
    print(out, len(font), "bytes")
