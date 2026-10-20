"""JJserver - MCP do REA (Reverse Engineering Everything) + tunel ngrok."""
import os, secrets, shutil, subprocess, threading, time
from pathlib import Path
from mcp.server.fastmcp import FastMCP
from mcp.server.transport_security import TransportSecuritySettings
from starlette.responses import JSONResponse

PORT = 8000
SECRET = os.environ.get("JJ_SECRET") or secrets.token_urlsafe(24)
WORK = Path(os.environ.get("REA_WORKDIR", str(Path.home() / "rea-work")))
WORK.mkdir(parents=True, exist_ok=True)
MAX = 20000
BINS = ["jadx", "apktool", "r2", "binwalk", "objdump", "strings", "gdb", "xxd", "file", "java"]

mcp = FastMCP(
    "REA", host="0.0.0.0", port=PORT, stateless_http=True, json_response=True,
    streamable_http_path=f"/{SECRET}/mcp",
    transport_security=TransportSecuritySettings(enable_dns_rebinding_protection=False),
)

@mcp.custom_route("/status", methods=["GET"])
async def status(request):
    return JSONResponse({"server": "JJserver/REA", "ok": True,
                         "tools": {b: bool(shutil.which(b)) for b in BINS}})

@mcp.tool()
def run(cmd: str, timeout: int = 120, cwd: str = "") -> str:
    """Executa um comando bash no Codespace (jadx, apktool, r2, binwalk, strings, objdump, gdb...)."""
    try:
        p = subprocess.run(["bash", "-lc", cmd], capture_output=True, text=True,
                           timeout=timeout, cwd=cwd or WORK)
        out = p.stdout + p.stderr
        cut = "\n...[cortado]" if len(out) > MAX else ""
        return f"[exit {p.returncode}]\n{out[:MAX]}{cut}"
    except subprocess.TimeoutExpired:
        return f"[timeout apos {timeout}s]"

@mcp.tool()
def read_file(path: str, max_bytes: int = MAX) -> str:
    """Le um arquivo de texto (relativo ao workdir ou absoluto)."""
    p = Path(path) if os.path.isabs(path) else WORK / path
    return p.read_bytes()[:max_bytes].decode("utf-8", "replace")

@mcp.tool()
def write_file(path: str, content: str) -> str:
    """Escreve um arquivo de texto (cria pastas se precisar)."""
    p = Path(path) if os.path.isabs(path) else WORK / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content)
    return f"escrito: {p}"

@mcp.tool()
def list_dir(path: str = ".") -> str:
    """Lista arquivos de uma pasta."""
    p = Path(path) if os.path.isabs(path) else WORK / path
    return "\n".join(f"{'d' if x.is_dir() else '-'} {x.name}" for x in sorted(p.iterdir()))

@mcp.tool()
def rea_tools() -> dict:
    """Mostra quais ferramentas de REA estao instaladas e o workdir."""
    return {"workdir": str(WORK), "tools": {b: bool(shutil.which(b)) for b in BINS}}

def _heartbeat():
    """Imprime a cada 60s: atividade de terminal reseta o timer de ociosidade do Codespace."""
    while True:
        time.sleep(60)
        print("heartbeat: JJserver vivo", flush=True)

if __name__ == "__main__":
    threading.Thread(target=_heartbeat, daemon=True).start()
    token = os.environ.get("NGROK_AUTHTOKEN")
    if token:
        from pyngrok import ngrok, conf
        conf.get_default().auth_token = token
        dom = os.environ.get("NGROK_DOMAIN")  # dominio estatico gratuito do ngrok (URL nao muda)
        opts = {"domain": dom} if dom else {}
        pub = ngrok.connect(PORT, "http", **opts).public_url.replace("http://", "https://")
        print(f"\n=== COLE NO CLAUDE / JJserver APK ===\n{pub}/{SECRET}/mcp\n")
    else:
        print(f"Sem NGROK_AUTHTOKEN. Local: http://localhost:{PORT}/{SECRET}/mcp")
    mcp.run(transport="streamable-http")
