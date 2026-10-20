#!/usr/bin/env bash
# Instala as ferramentas de REA no Codespace (rode 1 vez)
set -e
sudo apt-get update -y
for p in default-jre-headless binwalk apktool radare2 gdb binutils xxd file unzip curl; do sudo apt-get install -y $p || echo "pulou $p"; done
pip install -r "$(dirname "$0")/requirements.txt"
if ! command -v jadx >/dev/null; then
  URL=$(curl -s https://api.github.com/repos/skylot/jadx/releases/latest | grep browser_download_url | grep -E 'jadx-[0-9.]+\.zip' | head -1 | cut -d'"' -f4)
  curl -L "$URL" -o /tmp/jadx.zip
  sudo unzip -oq /tmp/jadx.zip -d /opt/jadx
  sudo ln -sf /opt/jadx/bin/jadx /usr/local/bin/jadx
fi
echo "OK! Agora: python server/server.py"
