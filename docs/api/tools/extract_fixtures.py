"""Write the JSON examples of docs/api/api-spec.md as test fixtures for :core:testing (technical-design §14).

Usage (from the repo root):
    python3 docs/api/tools/extract_fixtures.py docs/api/api-spec.md core/testing/src/main/resources/fixtures
    python3 docs/api/tools/extract_fixtures.py --check [--stamp FILE] docs/api/api-spec.md <fixtures dir>

Each example is written as <name>.json, byte-identical to the block in the spec (names in spec_examples.py).
Extraction removes stale .json files. --check writes nothing to the fixtures directory and exits with 1 when
a fixture is missing, differs or is not in the spec. --stamp writes FILE on success, for Gradle up-to-date
checks. Standard library only.
"""
import argparse
import os
import sys

sys.dont_write_bytecode = True  # keep docs/api/tools free of __pycache__ (a Gradle task input)
from spec_examples import read_examples  # noqa: E402

FIX_HINT = "Run: python3 docs/api/tools/extract_fixtures.py docs/api/api-spec.md <fixtures dir>  (or ./gradlew extractApiFixtures)"


def expected_files(spec):
    return {f"{name}.json": raw for name, raw, _parsed in read_examples(spec)}


def existing_files(directory):
    if not os.path.isdir(directory):
        return set()
    return {f for f in os.listdir(directory) if f.endswith(".json")}


def read_bytes(path):
    with open(path, "rb") as f:
        return f.read()


def check(expected, directory):
    present = existing_files(directory)
    missing = sorted(set(expected) - present)
    extra = sorted(present - set(expected))
    differing = sorted(
        f for f in set(expected) & present
        if read_bytes(os.path.join(directory, f)) != expected[f].encode("utf-8")
    )
    for label, files in (("missing", missing), ("differs from api-spec.md", differing), ("not in api-spec.md", extra)):
        for f in files:
            print(f"fixture {label}: {os.path.join(directory, f)}", file=sys.stderr)
    if missing or differing or extra:
        print(f"API fixtures are out of date. {FIX_HINT}", file=sys.stderr)
        return False
    print(f"{len(expected)} API fixtures match api-spec.md")
    return True


def extract(expected, directory):
    os.makedirs(directory, exist_ok=True)
    for stale in existing_files(directory) - set(expected):
        os.remove(os.path.join(directory, stale))
    for f, raw in expected.items():
        with open(os.path.join(directory, f), "wb") as out:
            out.write(raw.encode("utf-8"))
    print(f"wrote {len(expected)} fixtures to {directory}")


def write_stamp(path):
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write("ok\n")


def main(argv):
    parser = argparse.ArgumentParser(description="Extract the api-spec.md JSON examples as fixtures.")
    parser.add_argument("--check", action="store_true", help="fail if the fixtures differ instead of writing them")
    parser.add_argument("--stamp", help="file to write on success")
    parser.add_argument("spec", help="path to api-spec.md")
    parser.add_argument("fixtures", help="fixtures directory")
    args = parser.parse_args(argv)

    try:
        expected = expected_files(args.spec)
    except (OSError, ValueError) as e:
        print(f"error: {e}", file=sys.stderr)
        return 2
    if args.check:
        if not check(expected, args.fixtures):
            return 1
    else:
        extract(expected, args.fixtures)
    if args.stamp:
        write_stamp(args.stamp)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
