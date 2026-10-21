#!/usr/bin/env python3
"""Check 64-bit native ELF load/RELRO alignment and uncompressed APK placement."""
import argparse
import hashlib
import json
from pathlib import Path
import struct
import zipfile

PAGE_SIZE = 16_384


def inspect_elf(name, data):
    result = {"library": name, "sha256": hashlib.sha256(data).hexdigest(), "errors": []}
    errors = result["errors"]
    if len(data) < 64 or data[:6] != b"\x7fELF\x02\x01":
        errors.append("Expected a little-endian ELF64 library")
        return result
    machine = struct.unpack_from("<H", data, 18)[0]
    if machine not in (62, 183):
        errors.append(f"Unexpected 64-bit architecture: {machine}")
    offset = struct.unpack_from("<Q", data, 32)[0]
    entry_size, count = struct.unpack_from("<HH", data, 54)
    if entry_size < 56 or offset + entry_size * count > len(data):
        errors.append("Invalid program header table")
        return result
    loads, relro_ends, writable_ranges, relro_ranges = [], [], [], []
    for index in range(count):
        kind, flags, file_offset, address, _, file_size, memory_size, alignment = struct.unpack_from(
            "<IIQQQQQQ", data, offset + entry_size * index,
        )
        if kind == 1:
            loads.append(alignment)
            if flags & 2:
                writable_ranges.append((address, address + memory_size))
            if alignment < PAGE_SIZE or alignment & (alignment - 1):
                errors.append(f"LOAD alignment {alignment:#x} is not compatible with 16 KB pages")
            if file_offset % PAGE_SIZE != address % PAGE_SIZE:
                errors.append("LOAD file offset and virtual address are not congruent modulo 16 KB")
            if file_size > memory_size or file_offset + file_size > len(data):
                errors.append("Invalid LOAD segment size")
        elif kind == 0x6474E552:  # PT_GNU_RELRO
            end = address + memory_size
            relro_ends.append(end)
            relro_ranges.append((address, end))
    # Modern LLD may leave a gap before the next writable LOAD. A non-page-aligned
    # RELRO end alone is not a failure: reject rounding only if it protects live
    # writable bytes outside RELRO, matching Bionic's page-rounded mprotect range.
    for start, end in relro_ranges:
        rounded_start = start // PAGE_SIZE * PAGE_SIZE
        rounded_end = (end + PAGE_SIZE - 1) // PAGE_SIZE * PAGE_SIZE
        for lo, hi in writable_ranges:
            before = max(lo, rounded_start) < min(hi, start)
            after = max(lo, end) < min(hi, rounded_end)
            if before or after:
                errors.append("16 KB-rounded RELRO overlaps writable LOAD bytes outside RELRO")
                break
    if not loads:
        errors.append("No LOAD segments")
    result.update(load_alignments=loads, relro_ends=relro_ends)
    return result


def inspect_path(path):
    if path.is_dir():
        return [inspect_elf(str(p.relative_to(path)), p.read_bytes())
                for p in sorted(path.rglob("*.so"))
                if "arm64-v8a" in p.parts or "x86_64" in p.parts]
    results = []
    with zipfile.ZipFile(path) as apk, path.open("rb") as raw:
        for entry in sorted(apk.infolist(), key=lambda info: info.filename):
            if not entry.filename.startswith(("lib/arm64-v8a/", "lib/x86_64/")) or not entry.filename.endswith(".so"):
                continue
            result = inspect_elf(entry.filename, apk.read(entry))
            if entry.compress_type == zipfile.ZIP_STORED:
                raw.seek(entry.header_offset)
                header = raw.read(30)
                if len(header) != 30 or header[:4] != b"PK\x03\x04":
                    result["errors"].append("Invalid local ZIP header")
                else:
                    name_size, extra_size = struct.unpack_from("<HH", header, 26)
                    data_offset = entry.header_offset + 30 + name_size + extra_size
                    result["zip_data_offset"] = data_offset
                    if data_offset % PAGE_SIZE:
                        result["errors"].append("Uncompressed native library is not ZIP-aligned to 16 KB")
            results.append(result)
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("input", type=Path, help="APK or native-library source directory")
    parser.add_argument("--json-output", type=Path)
    args = parser.parse_args()
    results = inspect_path(args.input)
    passed = bool(results) and all(not row["errors"] for row in results)
    report = {"input": str(args.input), "page_size": PAGE_SIZE, "passed": passed, "libraries": results}
    if args.json_output:
        args.json_output.parent.mkdir(parents=True, exist_ok=True)
        args.json_output.write_text(json.dumps(report, indent=2), encoding="utf-8")
    for row in results:
        print(f"{'FAIL' if row['errors'] else 'PASS'} {row['library']}")
        for error in row["errors"]:
            print(f"  {error}")
    print(f"{len(results)} libraries checked; {'PASS' if passed else 'FAIL'}")
    raise SystemExit(0 if passed else 1)


if __name__ == "__main__":
    main()
