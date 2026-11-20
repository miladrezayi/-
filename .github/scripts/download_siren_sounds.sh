#!/usr/bin/env bash
set -euo pipefail

RAW_DIR="app/src/main/res/raw"
mkdir -p "$RAW_DIR"

download_sound() {
  local name="$1"
  local url="$2"
  local target="$RAW_DIR/$name.mp3"

  echo "Downloading $name"
  curl --fail --silent --show-error --location \
    --retry 4 --retry-delay 2 --connect-timeout 20 --max-time 180 \
    "$url" --output "$target"

  if [ ! -s "$target" ]; then
    echo "Downloaded file is empty: $target" >&2
    exit 1
  fi

  local size
  size=$(stat -c%s "$target")
  if [ "$size" -lt 2048 ]; then
    echo "Downloaded file is unexpectedly small ($size bytes): $target" >&2
    exit 1
  fi
}

# CC0 field recordings from SFX Jar / BigSoundBank.
download_sound "police_siren_fr_outdoor" "https://audio.sfxjar.com/audio/0886.mp3"
download_sound "police_siren_fr_passing" "https://audio.sfxjar.com/audio/2029.mp3"
download_sound "ambulance_siren_outdoor" "https://audio.sfxjar.com/audio/1463.mp3"
download_sound "ambulance_siren_engine" "https://audio.sfxjar.com/audio/1465.mp3"
download_sound "gendarmerie_motorcycle_siren" "https://audio.sfxjar.com/audio/2760.mp3"
download_sound "national_alert_siren" "https://audio.sfxjar.com/audio/3257.mp3"

# Recorded Midland G7 radio sounds and real radio-channel interference.
download_sound "walkie_start" "https://audio.sfxjar.com/audio/0589.mp3"
download_sound "walkie_keypad" "https://audio.sfxjar.com/audio/0590.mp3"
download_sound "walkie_conversation" "https://audio.sfxjar.com/audio/0591.mp3"
download_sound "radio_interference" "https://audio.sfxjar.com/audio/0310.mp3"

echo "All offline sound assets downloaded and checked."
