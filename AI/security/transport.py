"""HMAC authenticated internal transport; TLS still required off loopback."""
import hashlib
import hmac
import os
import time
from collections import OrderedDict
from starlette.responses import JSONResponse

class InternalAuthMiddleware:
    def __init__(self, app):
        self.app = app
        self.key = os.environ.get("AI_INTERNAL_SECRET", "")
        if len(self.key) < 32:
            raise RuntimeError("AI_INTERNAL_SECRET phải có ít nhất 32 ký tự")
        self.seen = OrderedDict()

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http":
            return await self.app(scope, receive, send)
        chunks = []
        size = 0
        while True:
            msg = await receive()
            if msg["type"] == "http.disconnect":
                return
            chunk = msg.get("body", b"")
            size += len(chunk)
            if size > 12 * 1024 * 1024:
                return await JSONResponse({"error": "Payload too large"}, 413)(scope, receive, send)
            chunks.append(chunk)
            if not msg.get("more_body", False):
                break
        body = b"".join(chunks)
        headers = dict(scope["headers"])
        def header(name):
            return headers.get(name.encode(), b"").decode("ascii", errors="replace")
        timestamp, nonce, user = (header(n) for n in ("x-ai-timestamp", "x-ai-nonce", "x-ai-user"))
        target = scope.get("raw_path", scope["path"].encode()).decode("ascii")
        if scope.get("query_string"):
            target += "?" + scope["query_string"].decode("ascii")
        canonical = "\n".join([scope["method"], target, timestamp, nonce, user, hashlib.sha256(body).hexdigest()])
        expected = hmac.new(self.key.encode(), canonical.encode(), hashlib.sha256).hexdigest()
        now = time.time()
        while self.seen and next(iter(self.seen.values())) < now - 60:
            self.seen.popitem(last=False)
        try:
            fresh = abs(now - int(timestamp)) <= 60
        except ValueError:
            fresh = False
        if not fresh or not nonce or not user or nonce in self.seen or not hmac.compare_digest(expected, header("x-ai-signature")):
            return await JSONResponse({"error": "Invalid internal authentication"}, 401)(scope, receive, send)
        if len(self.seen) >= 10000:
            return await JSONResponse({"error": "Service busy"}, 503)(scope, receive, send)
        self.seen[nonce] = now
        scope.setdefault("state", {})["ai_user"] = user
        sent = False
        async def replay():
            nonlocal sent
            if not sent:
                sent = True
                return {"type": "http.request", "body": body, "more_body": False}
            return await receive()
        await self.app(scope, replay, send)
