"""Minimal byte-exact NBT reader/writer for level.dat edits (gzip, big-endian).

Values are (tag_type, payload). Names and strings stay as raw bytes so a round trip is exact.
"""

import gzip
import struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class _Reader:
    def __init__(self, data):
        self.d = data
        self.i = 0

    def take(self, n):
        b = self.d[self.i : self.i + n]
        self.i += n
        return b

    def unpack(self, fmt):
        size = struct.calcsize(fmt)
        return struct.unpack(fmt, self.take(size))[0]

    def string(self):
        return self.take(self.unpack(">H"))

    def payload(self, t):
        if t == BYTE:
            return self.unpack(">b")
        if t == SHORT:
            return self.unpack(">h")
        if t == INT:
            return self.unpack(">i")
        if t == LONG:
            return self.unpack(">q")
        if t == FLOAT:
            return self.unpack(">f")
        if t == DOUBLE:
            return self.unpack(">d")
        if t == BYTE_ARRAY:
            return self.take(self.unpack(">i"))
        if t == STRING:
            return self.string()
        if t == LIST:
            et = self.unpack(">b")
            n = self.unpack(">i")
            return (et, [self.payload(et) for _ in range(n)])
        if t == COMPOUND:
            out = {}
            while True:
                ct = self.unpack(">b")
                if ct == END:
                    return out
                name = self.string()
                out[name] = (ct, self.payload(ct))
        if t == INT_ARRAY:
            n = self.unpack(">i")
            return list(struct.unpack(">%di" % n, self.take(4 * n)))
        if t == LONG_ARRAY:
            n = self.unpack(">i")
            return list(struct.unpack(">%dq" % n, self.take(8 * n)))
        raise ValueError("bad tag %d" % t)


def _write(t, v, out):
    if t == BYTE:
        out += struct.pack(">b", v)
    elif t == SHORT:
        out += struct.pack(">h", v)
    elif t == INT:
        out += struct.pack(">i", v)
    elif t == LONG:
        out += struct.pack(">q", v)
    elif t == FLOAT:
        out += struct.pack(">f", v)
    elif t == DOUBLE:
        out += struct.pack(">d", v)
    elif t == BYTE_ARRAY:
        out += struct.pack(">i", len(v)) + v
    elif t == STRING:
        out += struct.pack(">H", len(v)) + v
    elif t == LIST:
        et, items = v
        out += struct.pack(">bi", et, len(items))
        for item in items:
            _write(et, item, out)
    elif t == COMPOUND:
        for name, (ct, cv) in v.items():
            out += struct.pack(">bH", ct, len(name)) + name
            _write(ct, cv, out)
        out += b"\x00"
    elif t == INT_ARRAY:
        out += struct.pack(">i%di" % len(v), len(v), *v)
    elif t == LONG_ARRAY:
        out += struct.pack(">i%dq" % len(v), len(v), *v)
    else:
        raise ValueError("bad tag %d" % t)


def load(path):
    """Returns (root_name, root_compound_dict)."""
    r = _Reader(gzip.decompress(open(path, "rb").read()))
    t = r.unpack(">b")
    assert t == COMPOUND, t
    name = r.string()
    return name, r.payload(COMPOUND)


def save(path, name, root):
    out = bytearray()
    out += struct.pack(">bH", COMPOUND, len(name)) + name
    _write(COMPOUND, root, out)
    open(path, "wb").write(gzip.compress(bytes(out)))
