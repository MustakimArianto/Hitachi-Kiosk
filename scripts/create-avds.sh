#!/usr/bin/env bash
# Creates the instrumented-test emulators of testing-plan §2.3: Kiosk-30 (API 30, 1080x1920, 320 dpi) and
# Kiosk-34 (API 34, 1080x1920, 240 dpi). Needs the Android SDK command-line tools (sdkmanager, avdmanager).
set -euo pipefail

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
bin="$sdk/cmdline-tools/latest/bin"
[[ -x "$bin/sdkmanager" ]] || {
    echo "Install the Android SDK Command-line Tools (SDK Manager > SDK Tools) into $sdk/cmdline-tools/latest" >&2
    exit 1
}

case "$(uname -m)" in
    arm64 | aarch64) abi=arm64-v8a ;;
    *) abi=x86_64 ;;
esac

create() {
    local name=$1 api=$2 density=$3
    local image="system-images;android-$api;google_apis;$abi"
    "$bin/sdkmanager" --install "$image" "platforms;android-$api" "emulator" "platform-tools"
    echo no | "$bin/avdmanager" create avd --force --name "$name" --package "$image" --device "pixel"
    local config="$HOME/.android/avd/$name.avd/config.ini"
    {
        echo "hw.lcd.width=1080"
        echo "hw.lcd.height=1920"
        echo "hw.lcd.density=$density"
        echo "hw.keyboard=yes"
        echo "showDeviceFrame=no"
    } >>"$config"
    echo "Created $name (API $api, 1080x1920, $density dpi)"
}

create Kiosk-30 30 320
create Kiosk-34 34 240
echo "Start one with: $sdk/emulator/emulator -avd Kiosk-30"
