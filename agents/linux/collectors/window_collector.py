import subprocess
from dataclasses import dataclass

@dataclass
class WindowInfo:
    process_name: str
    app_name: str
    window_title: str | None
    pid: int | None

class WindowCollector:
    def get_active_window(self) -> WindowInfo | None:
        win = self._try_xdotool()
        if not win:
            win = self._try_wmctrl()
        return win

    def _try_xdotool(self) -> WindowInfo | None:
        try:
            res_id = subprocess.run(['xdotool', 'getactivewindow'], capture_output=True, text=True)
            if res_id.returncode != 0: return None
            win_id = res_id.stdout.strip()
            
            res_pid = subprocess.run(['xdotool', 'getwindowpid', win_id], capture_output=True, text=True)
            if res_pid.returncode != 0: return None
            pid = int(res_pid.stdout.strip())
            
            res_name = subprocess.run(['xdotool', 'getwindowname', win_id], capture_output=True, text=True)
            title = res_name.stdout.strip() if res_name.returncode == 0 else None
            
            process_name = self._get_process_name_from_pid(pid)
            if not process_name: return None
            
            return WindowInfo(
                process_name=process_name,
                app_name=process_name.capitalize(),
                window_title=title,
                pid=pid
            )
        except Exception:
            return None

    def _try_wmctrl(self) -> WindowInfo | None:
        return None

    def _get_process_name_from_pid(self, pid: int) -> str | None:
        try:
            with open(f"/proc/{pid}/comm", "r") as f:
                return f.read().strip()
        except Exception:
            return None
