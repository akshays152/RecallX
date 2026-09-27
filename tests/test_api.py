import json
import os
import signal
import socket
import subprocess
import sys
import tempfile
import time
import unittest
from pathlib import Path
from urllib.error import URLError
from urllib.request import Request, urlopen


class ApiIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        try:
            import fastapi
            import uvicorn
        except ImportError:
            raise unittest.SkipTest("API extra not installed")
        cls.directory = tempfile.TemporaryDirectory()
        sock = socket.socket()
        sock.bind(("127.0.0.1", 0))
        cls.port = sock.getsockname()[1]
        sock.close()
        cls.root = f"http://127.0.0.1:{cls.port}"
        environment = dict(os.environ)
        cls.process = subprocess.Popen(
            [sys.executable, "-m", "recallx_engine", "--db", str(Path(cls.directory.name) / "api.db"),
             "serve", "--host", "127.0.0.1", "--port", str(cls.port)],
            cwd=str(Path(__file__).resolve().parents[1]), env=environment,
            creationflags=subprocess.CREATE_NEW_PROCESS_GROUP if os.name == "nt" else 0,
            stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
        )
        for _ in range(60):
            if cls.process.poll() is not None:
                raise RuntimeError(cls.process.stderr.read().decode(errors="replace"))
            try:
                with urlopen(cls.root + "/health", timeout=1) as response:
                    if response.status == 200:
                        return
            except (URLError, TimeoutError):
                time.sleep(0.1)
        raise RuntimeError("API server did not start")

    @classmethod
    def tearDownClass(cls):
        if cls.process.poll() is None:
            if os.name == "nt":
                # CTRL_BREAK lets Uvicorn run FastAPI's shutdown lifecycle and
                # close RecallEngine's sqlite3 connection before exit.
                try:
                    cls.process.send_signal(signal.CTRL_BREAK_EVENT)
                    cls.process.wait(timeout=10)
                except (OSError, subprocess.TimeoutExpired):
                    cls.process.kill()
                    cls.process.wait(timeout=10)
            else:
                cls.process.terminate()
                cls.process.wait(timeout=10)
        cls.process.stderr.close()
        # Windows may release a just-closed child-process handle a fraction
        # after wait() returns. Retry cleanup briefly without hiding failures.
        for attempt in range(20):
            try:
                cls.directory.cleanup()
                return
            except PermissionError:
                if attempt == 19:
                    raise
                time.sleep(0.1)

    def request_json(self, path, method="GET", payload=None, headers=None):
        body = json.dumps(payload).encode() if payload is not None else None
        request = Request(self.root + path, data=body, method=method, headers=headers or {})
        if payload is not None:
            request.add_header("Content-Type", "application/json")
        with urlopen(request, timeout=30) as response:
            return response.status, json.load(response)

    def test_upload_search_open_delete(self):
        boundary = "recallx-test-boundary"
        file_bytes = b"Hotel Imperial booking price INR 7200"
        body = (
            f"--{boundary}\r\nContent-Disposition: form-data; name=\"source_uri\"\r\n\r\ncontent://demo/hotel\r\n"
            f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"hotel.txt\"\r\n"
            "Content-Type: text/plain\r\n\r\n"
        ).encode() + file_bytes + f"\r\n--{boundary}--\r\n".encode()
        request = Request(self.root + "/v1/memories/file", data=body, method="POST")
        request.add_header("Content-Type", f"multipart/form-data; boundary={boundary}")
        with urlopen(request, timeout=30) as response:
            self.assertEqual(response.status, 201)
            created = json.load(response)
        memory_id = created["memory"]["id"]
        self.assertTrue(created["created"])
        self.assertEqual(created["memory"]["content_url"], f"/v1/memories/{memory_id}/content")

        _, listed = self.request_json("/v1/memories")
        self.assertEqual(listed["total"], 1)
        self.assertTrue((Path(self.directory.name) / "api.db").is_file())
        _, searched = self.request_json("/v1/search", "POST", {"query": "hotel price"})
        self.assertEqual(searched["results"][0]["memory"]["id"], memory_id)
        with urlopen(self.root + f"/v1/memories/{memory_id}/content", timeout=30) as response:
            self.assertEqual(response.read(), file_bytes)
        _, deleted = self.request_json(f"/v1/memories/{memory_id}", "DELETE")
        self.assertTrue(deleted["deleted"])
        _, listed = self.request_json("/v1/memories")
        self.assertEqual(listed["total"], 0)
