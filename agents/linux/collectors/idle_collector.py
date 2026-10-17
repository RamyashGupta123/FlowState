import subprocess

class IdleCollector:
    def get_idle_seconds(self) -> int:
        try:
            res = subprocess.run(['xprintidle'], capture_output=True, text=True)
            if res.returncode == 0:
                return int(res.stdout.strip()) // 1000
        except Exception:
            pass
        return 0
