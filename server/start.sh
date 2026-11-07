#!/usr/bin/env bash
# Inicia (ou reinicia) o servidor em segundo plano. Log em /tmp/jj.log
cd "$(dirname "$0")/.."
pkill -f "server/server.py" 2>/dev/null
sleep 1
python -c "import mcp, pyngrok" 2>/dev/null || pip install -q -r server/requirements.txt --break-system-packages 2>/dev/null || pip install -q -r server/requirements.txt
setsid nohup python server/server.py > /tmp/jj.log 2>&1 &
echo "JJserver iniciado (log: /tmp/jj.log)"
