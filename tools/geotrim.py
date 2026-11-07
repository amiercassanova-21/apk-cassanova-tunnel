#!/usr/bin/env python3
"""Keep only some categories of a V2Ray/Xray geoip.dat or geosite.dat.

Both files are a protobuf list (GeoIPList / GeoSiteList): repeated field 1, each entry a message
whose field 1 is the category name (country_code). Entries are copied byte-for-byte, so the result
is a valid file for the same core. Used for Amier VPN Lite, which only ships the categories its
routing presets and DNS defaults use.

usage: geotrim.py IN OUT cat1 [cat2 ...]
"""
import sys


def varint(buf, pos):
    shift = result = 0
    while True:
        b = buf[pos]
        pos += 1
        result |= (b & 0x7F) << shift
        if not b & 0x80:
            return result, pos
        shift += 7


def entries(buf):
    """Yields (category, raw_entry_bytes_including_tag_and_length)."""
    pos = 0
    while pos < len(buf):
        start = pos
        key, pos = varint(buf, pos)
        if key != (1 << 3 | 2):
            raise ValueError(f"unexpected field key {key} at {start}")
        length, pos = varint(buf, pos)
        body = buf[pos:pos + length]
        pos += length
        name = ""
        bkey, bpos = varint(body, 0)
        if bkey == (1 << 3 | 2):
            nlen, bpos = varint(body, bpos)
            name = body[bpos:bpos + nlen].decode("utf-8")
        yield name, buf[start:pos]


def main():
    src, dst, *keep = sys.argv[1:]
    wanted = {k.lower() for k in keep}
    data = open(src, "rb").read()
    out, found = bytearray(), set()
    for name, raw in entries(data):
        if name.lower() in wanted:
            out += raw
            found.add(name.lower())
    missing = wanted - found
    if missing:
        sys.exit(f"{src}: categories not found: {sorted(missing)}")
    open(dst, "wb").write(out)
    print(f"{src}: kept {sorted(found)}, {len(data)} -> {len(out)} bytes")


if __name__ == "__main__":
    main()
