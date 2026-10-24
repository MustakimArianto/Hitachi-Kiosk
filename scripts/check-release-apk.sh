#!/usr/bin/env bash
# Release APK checks (TR-SEC-07, FR-019 AC3, TC-087, TC-148; technical-design §2.5, §11.1).
# Run by ./gradlew verify through :app:checkReleaseApk. Uses only the SDK build-tools (aapt2, dexdump).
#
# Usage: check-release-apk.sh <apk-dir> <r8-mapping-file> <sdk-dir> <build-tools-version> <stamp-file>
set -euo pipefail

apk_dir=$1
mapping=$2
sdk_dir=$3
build_tools=$4
stamp=$5

aapt2="$sdk_dir/build-tools/$build_tools/aapt2"
dexdump="$sdk_dir/build-tools/$build_tools/dexdump"
for tool in "$aapt2" "$dexdump"; do
    [[ -x "$tool" ]] || { echo "check-release-apk: missing $tool (install build-tools $build_tools)" >&2; exit 1; }
done

apk=$(find "$apk_dir" -maxdepth 1 -name '*.apk' | head -n 1)
[[ -n "$apk" ]] || { echo "check-release-apk: no APK in $apk_dir" >&2; exit 1; }

failures=()

# 1. No debug-only code: nothing from com.hitachi.kiosk.debug, no fakes, no switchable wrappers.
#    R8 renames classes, so the original names are checked in the mapping file as well as in the dex.
debug_class_pattern='^com\.hitachi\.kiosk\.(debug\.|([A-Za-z0-9_]+\.)*(Fake|Switchable)[A-Za-z0-9_$]* ->)'
if [[ -f "$mapping" ]] && grep -E -- "^[^ #].* -> " "$mapping" | grep -Eq -- "$debug_class_pattern"; then
    failures+=("debug-only classes in the R8 mapping: $(grep -E -- "^[^ #].* -> " "$mapping" | grep -E -- "$debug_class_pattern" | head -n 3 | tr '\n' ' ')")
fi
dex_dir=$(mktemp -d)
trap 'rm -rf "$dex_dir"' EXIT
unzip -q -o "$apk" 'classes*.dex' -d "$dex_dir"
for dex in "$dex_dir"/classes*.dex; do
    if "$dexdump" "$dex" 2>/dev/null | grep -E "Class descriptor" |
        grep -Eq "'Lcom/hitachi/kiosk/(debug/|([A-Za-z0-9_]+/)*(Fake|Switchable)[A-Za-z0-9_$]*;)"; then
        failures+=("debug-only classes in $(basename "$dex")")
    fi
done

# 2. No prototype tools: the design's "Prototype only" simulate bar (FR-019, C6).
if unzip -p "$apk" 'classes*.dex' resources.arsc 2>/dev/null | LC_ALL=C grep -aq "Prototype only"; then
    failures+=("the 'Prototype only' string is present")
fi

# 3. Manifest: not debuggable, no backup, only allow-listed exported components (technical-design §11.1).
manifest=$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk")
manifest_report=$(python3 -I -c '
import re, sys
allowed = {
    "com.hitachi.kiosk.MainActivity",
    "com.hitachi.kiosk.kiosk.KioskDeviceAdminReceiver",
    "com.hitachi.kiosk.kiosk.BootReceiver",
    "androidx.profileinstaller.ProfileInstallReceiver",
    "androidx.work.impl.diagnostics.DiagnosticsReceiver",
    # The WorkManager JobScheduler service for the periodic catalog sync (DEV-023); the system binds it only with
    # android.permission.BIND_JOB_SERVICE.
    "androidx.work.impl.background.systemjob.SystemJobService",
}
components = ("activity", "activity-alias", "receiver", "service", "provider")
problems, current, name, exported = [], None, None, False
def flush():
    if current in components and exported and name not in allowed:
        problems.append("exported component not in the allow-list: " + str(name))
for line in sys.stdin.read().splitlines():
    element = re.match(r"\s*E: ([\w-]+)", line)
    if element:
        flush()
        current, name, exported = element.group(1), None, False
        continue
    attr = re.match(r"\s*A: http://schemas.android.com/apk/res/android:(\w+)\([^)]*\)=(.*)", line)
    if not attr:
        continue
    key, value = attr.group(1), attr.group(2)
    if current == "application" and key == "debuggable" and "true" in value.lower() and "0x0" not in value:
        problems.append("android:debuggable is true")
    if current == "application" and key == "allowBackup" and not ("false" in value.lower() or "0x0" in value):
        problems.append("android:allowBackup is not false")
    if key == "name":
        found = re.search(r"\"([^\"]+)\"", value)
        name = found.group(1) if found else value
    if key == "exported":
        exported = "true" in value.lower() or "0xffffffff" in value
flush()
print("\n".join(problems))
' <<<"$manifest")
if ! grep -q "android:allowBackup" <<<"$manifest"; then
    manifest_report+=$'\nandroid:allowBackup is not set (it must be false)'
fi
while IFS= read -r problem; do
    [[ -n "$problem" ]] && failures+=("$problem")
done <<<"$manifest_report"

if ((${#failures[@]} > 0)); then
    echo "check-release-apk: $apk failed:" >&2
    printf '  - %s\n' "${failures[@]}" >&2
    exit 1
fi

mkdir -p "$(dirname "$stamp")"
echo "ok $(basename "$apk")" >"$stamp"
echo "check-release-apk: $(basename "$apk") passed"
