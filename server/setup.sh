#!/usr/bin/env bash
# Rapido: so instala o necessario para o servidor subir. As ferramentas de REA
# (jadx, apktool, radare2...) instalam em segundo plano por tools.sh.
D="$(cd "$(dirname "$0")" && pwd)"
mkdir -p ~/bin
pip install -q -r "$D/requirements.txt" --break-system-packages 2>/dev/null || pip install -q -r "$D/requirements.txt" || echo "AVISO: pip falhou"
setsid nohup bash "$D/tools.sh" > /tmp/setup.log 2>&1 &
exit 0
