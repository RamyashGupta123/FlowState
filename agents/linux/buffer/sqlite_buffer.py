import sqlite3
import json

class SqliteBuffer:
    def __init__(self, db_path: str):
        self.db_path = db_path
        self._init_db()

    def _init_db(self):
        with sqlite3.connect(self.db_path) as conn:
            conn.execute('''
                CREATE TABLE IF NOT EXISTS events (
                    event_id TEXT PRIMARY KEY,
                    payload TEXT,
                    sync_status TEXT DEFAULT 'pending',
                    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
                    attempts INT DEFAULT 0
                )
            ''')

    def insert_event(self, event: dict) -> bool:
        try:
            with sqlite3.connect(self.db_path) as conn:
                conn.execute('INSERT INTO events (event_id, payload) VALUES (?, ?)', 
                             (event['event_id'], json.dumps(event)))
                return True
        except sqlite3.IntegrityError:
            return False

    def get_pending_events(self, limit: int = 50) -> list[dict]:
        with sqlite3.connect(self.db_path) as conn:
            cur = conn.execute('SELECT payload FROM events WHERE sync_status = "pending" LIMIT ?', (limit,))
            return [json.loads(row[0]) for row in cur.fetchall()]

    def mark_synced(self, event_ids: list[str]):
        if not event_ids: return
        with sqlite3.connect(self.db_path) as conn:
            conn.execute(f'UPDATE events SET sync_status = "synced" WHERE event_id IN ({",".join("?" for _ in event_ids)})', event_ids)

    def mark_failed(self, event_ids: list[str]):
        if not event_ids: return
        with sqlite3.connect(self.db_path) as conn:
            conn.execute(f'UPDATE events SET sync_status = "failed" WHERE event_id IN ({",".join("?" for _ in event_ids)})', event_ids)

    def get_stats(self) -> dict:
        with sqlite3.connect(self.db_path) as conn:
            cur = conn.execute('SELECT sync_status, COUNT(*) FROM events GROUP BY sync_status')
            counts = {row[0]: row[1] for row in cur.fetchall()}
            return {
                'pending': counts.get('pending', 0),
                'synced': counts.get('synced', 0),
                'failed': counts.get('failed', 0)
            }
