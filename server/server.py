"""JJserver v4 - MCP do REA (Reverse Engineering Everything) + tunel ngrok.

Mudancas da v4:
- O servidor SEMPRE sobe, mesmo se o ngrok falhar.
- O ngrok roda em outra thread e tenta de novo para sempre, SOMENTE no dominio fixo
  (nada de cair para URL aleatoria, que o app nunca enxerga).
- /status mostra o estado do tunel e o motivo do erro.
"""
import os, shutil, subprocess, threading, time
from pathlib import Path
from mcp.server.fastmcp import FastMCP
from starlette.responses import JSONResponse

PORT = 8000
SECRET = os.environ.get("JJ_SECRET") or "JJsecreto2026xyz"
DOMAIN = os.environ.get("JJ_DOMAIN") or "subpanel-unreached-skinning.ngrok-free.dev"
os.environ["PATH"] = str(Path.home() / "bin") + os.pathsep + os.environ.get("PATH", "")
WORK = Path(os.environ.get("REA_WORKDIR", str(Path.home() / "rea-work")))
WORK.mkdir(parents=True, exist_ok=True)
MAX = 20000
BINS = ["jadx", "apktool", "r2", "binwalk", "objdump", "strings", "gdb", "xxd", "file", "java"]

TUNNEL = {"state": "iniciando", "url": None, "erro": None, "tentativas": 0}

mcp = FastMCP("REA", host="0.0.0.0", port=PORT, stateless_http=True, json_response=True,
              streamable_http_path=f"/{SECRET}/mcp")

@mcp.custom_route("/status", methods=["GET"])
async def status(request):
    return JSONResponse({"server": "JJserver/REA", "ok": True, "version": "4",
                         "tunnel": TUNNEL,
                         "tools": {b: bool(shutil.which(b)) for b in BINS}})

@mcp.tool()
def run(cmd: str, timeout: int = 120, cwd: str = "") -> str:
    """Executa um comando bash no Codespace (jadx, apktool, r2, binwalk, strings, objdump, gdb...)."""
    try:
        p = subprocess.run(["bash", "-lc", cmd], capture_output=True, text=True,
                           timeout=timeout, cwd=cwd or WORK, env=os.environ.copy())
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
    while True:
        time.sleep(60)
        print(f"heartbeat: JJserver vivo | tunel: {TUNNEL['state']} | {TUNNEL['erro'] or 'ok'}",
              flush=True)

def _explica(msg):
    m = msg.lower()
    if "108" in m or "simultaneous" in m:
        return ("limite de 1 sessao ngrok (plano free): ha outro agente ngrok ativo. "
                "Pare em dashboard.ngrok.com/agents (Stop) ou espere ~1 min.")
    if "334" in m or "already online" in m:
        return ("dominio ja esta em uso por outro endpoint (Codespace antigo ainda ligado?). "
                "Veja dashboard.ngrok.com/endpoints e pare o antigo.")
    if "authtoken" in m or "105" in m or "106" in m or "107" in m:
        return "authtoken invalido. Gere outro em dashboard.ngrok.com/get-started/your-authtoken"
    return msg[:250]

def _tunnel_loop():
    token = os.environ.get("NGROK_AUTHTOKEN", "").strip()
    if not token:
        TUNNEL.update(state="sem_token",
                      erro="NGROK_AUTHTOKEN nao existe neste Codespace. Crie o secret em "
                           "github.com/settings/codespaces (nome exato NGROK_AUTHTOKEN, "
                           "com acesso ao repo JJserver) e recrie o Codespace.")
        print("ERRO: " + TUNNEL["erro"], flush=True)
        print(f"Servidor so local: http://localhost:{PORT}/{SECRET}/mcp", flush=True)
        return
    from pyngrok import ngrok, conf
    conf.get_default().auth_token = token
    while True:
        TUNNEL["tentativas"] += 1
        TUNNEL["state"] = "conectando"
        try:
            ngrok.kill()
        except Exception:
            pass
        t = None
        err = None
        for opts in ({"url": "https://" + DOMAIN}, {"domain": DOMAIN}):
            try:
                t = ngrok.connect(addr=PORT, proto="http", **opts)
                break
            except Exception as e:
                err = _explica(str(e))
                print(f"[ngrok] tentativa {TUNNEL['tentativas']} {list(opts)[0]} falhou: {err}",
                      flush=True)
        if t is None:
            TUNNEL.update(state="erro", erro=err)
            time.sleep(20)
            continue
        pub = t.public_url.replace("http://", "https://")
        TUNNEL.update(state="no_ar", url=pub, erro=None)
        print(f"\n=== COLE NO CLAUDE / JJserver APK ===\n{pub}/{SECRET}/mcp\n", flush=True)
        if pub != "https://" + DOMAIN:
            print(f"AVISO: ngrok abriu {pub} e nao o dominio fixo {DOMAIN}", flush=True)
        while True:
            time.sleep(30)
            try:
                if not ngrok.get_tunnels():
                    raise RuntimeError("tunel caiu")
            except Exception as e:
                TUNNEL.update(state="reconectando", erro=str(e)[:200])
                print(f"[ngrok] tunel perdido ({e}); reconectando...", flush=True)
                break

if __name__ == "__main__":
    threading.Thread(target=_heartbeat, daemon=True).start()
    threading.Thread(target=_tunnel_loop, daemon=True).start()
    mcp.run(transport="streamable-http")
