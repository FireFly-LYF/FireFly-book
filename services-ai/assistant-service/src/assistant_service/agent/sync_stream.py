"""同步流 → 异步流的线程桥（LLM 流式输出基础设施，避免阻塞事件循环）。"""

from __future__ import annotations

import asyncio
import threading
from collections.abc import AsyncIterator, Iterator


async def stream_sync_in_thread(sync_iter: Iterator[str]) -> AsyncIterator[str]:
    """在后台线程消费同步 iterator，异步 yield 各元素。"""
    loop = asyncio.get_running_loop()
    q: asyncio.Queue[tuple[bool, object]] = asyncio.Queue()

    def _worker() -> None:
        try:
            for item in sync_iter:
                loop.call_soon_threadsafe(q.put_nowait, (True, item))
            loop.call_soon_threadsafe(q.put_nowait, (False, None))
        except Exception as exc:
            loop.call_soon_threadsafe(q.put_nowait, (False, exc))

    threading.Thread(target=_worker, daemon=True).start()

    while True:
        ok, val = await q.get()
        if ok:
            yield str(val)
        elif isinstance(val, Exception):
            raise val
        else:
            return
