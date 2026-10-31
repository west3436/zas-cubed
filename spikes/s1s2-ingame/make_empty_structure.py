"""Writes a gzipped 1x1x1 empty structure template for GameTests."""
import gzip
import struct
import sys


def name(s):
    b = s.encode()
    return struct.pack('>H', len(b)) + b


def tag(t, n, payload):
    return bytes([t]) + name(n) + payload


def int_list(n, values):
    return tag(9, n, bytes([3]) + struct.pack('>i', len(values)) + b''.join(struct.pack('>i', v) for v in values))


def empty_list(n):
    return tag(9, n, bytes([0]) + struct.pack('>i', 0))


air = tag(8, 'Name', name('minecraft:air')) + b'\x00'
palette = tag(9, 'palette', bytes([10]) + struct.pack('>i', 1) + air)
root = (tag(3, 'DataVersion', struct.pack('>i', 3955)) + int_list('size', [1, 1, 1]) + palette
        + empty_list('blocks') + empty_list('entities'))
data = bytes([10]) + name('') + root + b'\x00'
with gzip.open(sys.argv[1], 'wb') as f:
    f.write(data)
