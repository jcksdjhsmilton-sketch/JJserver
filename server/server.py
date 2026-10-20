"""JJserver - MCP do REA + tunel ngrok."""
import os, secrets, shutil, subprocess, threading, time
from pathlib import Path
from mcp.server.fastmcp import FastMCP
from starlette.responses import JSONResponse

PORT = 8000
SECRET = os.environ.get("JJ_SECRET") or secrets.token_urlsafe(24)
DOMAIN = os.environ.get("NGROK_DOMAIN") or "subpanel-unreached-skinning.ngrok-free.dev"
WORK = Path(os.environ.get("REA_WORKDIR", str(Path.home() / "rea-work")))
WORK.mkdir(parents=True, exist_ok=True)
MAX = 20000
BINS = ["jadx", "apktool", "r2", "binwalk", "objdump", "strings", "gdb", "xxd", "file", "java"]

mcp = FastMCP("REA", host="0.0.0.0", port=PORT, stateless_http=True, json_response=True,
              streamable_http_path=f"/{SECRET}/mcp")

@mcp.custom_route("/status", methods=["GET"])
async def status(request):
    return JSONResponse({"server": "JJserver/REA", "ok": True,
                         "tools": {b: bool(shutil.which(b)) for b in BINS}})

@mcp.tool()
def run(cmd: str, timeout: int = 120, cwd: str = "") -> str:
    """Executa um comando bash no Codespace."""
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
    """Le um arquivo de texto."""
    p = Path(path) if os.path.isabs(path) else WORK / path
    return p.read_bytes()[:max_bytes].decode("utf-8", "replace")

@mcp.tool()
def write_file(path: str, content: str) -> str:
    """Escreve um arquivo de texto."""
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
    """Mostra as ferramentas de REA instaladas."""
    return {"workdir": str(WORK), "tools": {b: bool(shutil.which(b)) for b in BINS}}

def _heartbeat():
    while True:
        time.sleep(60)
        print("heartbeat: JJserver vivo", flush=True)

def _open_tunnel():
    from pyngrok import ngrok, conf
    token = os.environ.get("NGROK_AUTHTOKEN")
    if token:
        conf.get_default().auth_token = token
    last_err = None
    for opts in [{"domain": DOMAIN}, {}]:
        try:
            t = ngrok.connect(addr=PORT, proto="http", **opts)
            return t.public_url.replace("http://", "https://")
        except Exception as e:
            last_err = e
            print(f"[ngrok] tentativa {opts or 'sem dominio'} falhou: {str(e)[:200]}", flush=True)
    raise RuntimeError(f"ngrok nao conectou: {last_err}")

if __name__ == "__main__":
    threading.Thread(target=_heartbeat, daemon=True).start()
    if os.environ.get("NGROK_AUTHTOKEN") or os.environ.get("NGROK_DOMAIN"):
        pub = _open_tunnel()
        print(f"\n=== COLE NO CLAUDE / JJserver APK ===\n{pub}/{SECRET}/mcp\n", flush=True)
    else:
        print(f"Sem NGROK_AUTHTOKEN. Local: http://localhost:{PORT}/{SECRET}/mcp", flush=True)
    mcp.run(transport="streamable-http")
FIMcat > server/server.py << 'FIM'
"""JJserver - MCP do REA + tunel ngrok."""
import os, secrets, shutil, subprocess, threading, time
from pathlib import Path
from mcp.server.fastmcp import FastMCP
from starlette.responses import JSONResponse

PORT = 8000
SECRET = os.environ.get("JJ_SECRET") or secrets.token_urlsafe(24)
DOMAIN = os.environ.get("NGROK_DOMAIN") or "subpanel-unreached-skinning.ngrok-free.dev"
WORK = Path(os.environ.get("REA_WORKDIR", str(Path.home() / "rea-work")))
WORK.mkdir(parents=True, exist_ok=True)
MAX = 20000
BINS = ["jadx", "apktool", "r2", "binwalk", "objdump", "strings", "gdb", "xxd", "file", "java"]

mcp = FastMCP("REA", host="0.0.0.0", port=PORT, stateless_http=True, json_response=True,
              streamable_http_path=f"/{SECRET}/mcp")

@mcp.custom_route("/status", methods=["GET"])
async def status(request):
    return JSONResponse({"server": "JJserver/REA", "ok": True,
                         "tools": {b: bool(shutil.which(b)) for b in BINS}})

@mcp.tool()
def run(cmd: str, timeout: int = 120, cwd: str = "") -> str:
    """Executa um comando bash no Codespace."""
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
    """Le um arquivo de texto."""
    p = Path(path) if os.path.isabs(path) else WORK / path
    return p.read_bytes()[:max_bytes].decode("utf-8", "replace")

@mcp.tool()
def write_file(path: str, content: str) -> str:
    """Escreve um arquivo de texto."""
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
    """Mostra as ferramentas de REA instaladas."""
    return {"workdir": str(WORK), "tools": {b: bool(shutil.which(b)) for b in BINS}}

def _heartbeat():
    while True:
        time.sleep(60)
        print("heartbeat: JJserver vivo", flush=True)

def _open_tunnel():
    from pyngrok import ngrok, conf
    token = os.environ.get("NGROK_AUTHTOKEN")
    if token:
        conf.get_default().auth_token = token
    last_err = None
    for opts in [{"domain": DOMAIN}, {}]:
        try:
            t = ngrok.connect(addr=PORT, proto="http", **opts)
            return t.public_url.replace("http://", "https://")
        except Exception as e:
            last_err = e
            print(f"[ngrok] tentativa {opts or 'sem dominio'} falhou: {str(e)[:200]}", flush=True)
    raise RuntimeError(f"ngrok nao conectou: {last_err}")

if __name__ == "__main__":
    threading.Thread(target=_heartbeat, daemon=True).start()
    if os.environ.get("NGROK_AUTHTOKEN") or os.environ.get("NGROK_DOMAIN"):
        pub = _open_tunnel()
        print(f"\n=== COLE NO CLAUDE / JJserver APK ===\n{pub}/{SECRET}/mcp\n", flush=True)
    else:
        print(f"Sem NGROK_AUTHTOKEN. Local: http://localhost:{PORT}/{SECRET}/mcp", flush=True)
    mcp.run(transport="streamable-http")
