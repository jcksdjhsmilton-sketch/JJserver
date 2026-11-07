#!/usr/bin/env bash
# Instala as ferramentas de REA (jadx, apktool, radare2...) em ~/bin. Log: /tmp/setup.log
mkdir -p ~/bin
sudo apt-get update -y >/dev/null 2>&1
for p in default-jre-headless radare2 gdb binutils xxd file unzip curl binwalk; do
  sudo apt-get install -y $p >/dev/null 2>&1 || echo "pulou $p"
done
if [ ! -x ~/bin/jadx ]; then
  URL=$(curl -s https://api.github.com/repos/skylot/jadx/releases/latest | grep browser_download_url | grep -E 'jadx-[0-9.]+\.zip' | head -1 | cut -d'"' -f4)
  [ -z "$URL" ] && URL="https://github.com/skylot/jadx/releases/download/v1.5.0/jadx-1.5.0.zip"
  curl -sL "$URL" -o /tmp/jadx.zip && rm -rf ~/jadx && unzip -oq /tmp/jadx.zip -d ~/jadx && ln -sf ~/jadx/bin/jadx ~/bin/jadx
fi
if [ ! -s ~/bin/apktool.jar ]; then
  curl -sL https://github.com/iBotPeaches/Apktool/releases/download/v2.9.3/apktool_2.9.3.jar -o ~/bin/apktool.jar
  curl -sL https://raw.githubusercontent.com/iBotPeaches/Apktool/v2.9.3/scripts/linux/apktool -o ~/bin/apktool
  chmod +x ~/bin/apktool
fi
echo "Ferramentas instaladas."
