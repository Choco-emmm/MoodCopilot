"""Stream a local release archive with byte counts; never inspect its content."""
import sys
import time

started = last_report = time.monotonic()
sent = 0
try:
    with open(sys.argv[1], "rb") as archive:
        while chunk := archive.read(256 * 1024):
            sys.stdout.buffer.write(chunk)
            sys.stdout.buffer.flush()
            sent += len(chunk)
            now = time.monotonic()
            if now - last_report >= 10:
                print(f"[transfer] handed to SSH: {sent} bytes, {sent / max(now - started, 0.001):.0f} bytes/s", file=sys.stderr, flush=True)
                last_report = now
    print(f"[transfer] handed to SSH: {sent} bytes in {time.monotonic() - started:.1f}s", file=sys.stderr, flush=True)
except BrokenPipeError:
    # The SSH command failed; return a failing pipeline result without a body dump.
    sys.exit(1)
