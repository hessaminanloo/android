#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET="${ROOT_DIR}/third_party/whisper.cpp"

if [[ -d "${TARGET}/.git" ]]; then
  echo "whisper.cpp already exists at ${TARGET}"
  exit 0
fi

mkdir -p "${ROOT_DIR}/third_party"
git clone --depth 1 https://github.com/ggml-org/whisper.cpp "${TARGET}"
echo "whisper.cpp is ready. Open the project in Android Studio and install NDK 25.2.9519653 + CMake 3.22.1."
