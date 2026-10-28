"""Fotos de prueba para el simulador (degradados con un círculo), sin librerías externas."""
import struct
import sys
import zlib
from pathlib import Path


def png(path, width, height, pixel):
    rows = b"".join(b"\x00" + b"".join(bytes(pixel(x, y)) for x in range(width)) for y in range(height))

    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    Path(path).write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(rows, 6)) + chunk(b"IEND", b""))


def photo(top, bottom, sun, width, height):
    cx, cy, r = width * 0.6, height * 0.35, min(width, height) * 0.12

    def pixel(x, y):
        if (x - cx) ** 2 + (y - cy) ** 2 < r * r:
            return sun
        t = y / (height - 1)
        return tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))

    return pixel


out = Path(sys.argv[1])
out.mkdir(parents=True, exist_ok=True)
# Distintas formas: vertical, apaisada y cuadrada (para ver el ajuste a la pantalla).
png(out / "atardecer.png", 360, 640, photo((255, 140, 90), (90, 40, 120), (255, 220, 120), 360, 640))
png(out / "mar.png", 640, 400, photo((40, 120, 200), (10, 30, 70), (240, 240, 255), 640, 400))
png(out / "bosque.png", 480, 480, photo((140, 200, 120), (20, 70, 40), (255, 250, 200), 480, 480))
print("ok")
