import tempfile
import os
from buffer.sqlite_buffer import SqliteBuffer

def test_insert_and_retrieve():
    fd, db_path = tempfile.mkstemp()
    os.close(fd)
    try:
        buf = SqliteBuffer(db_path)
        ev = {'event_id': '1'}
        assert buf.insert_event(ev)
        assert not buf.insert_event(ev) # duplicate
        
        pending = buf.get_pending_events()
        assert len(pending) == 1
        assert pending[0]['event_id'] == '1'
    finally:
        os.unlink(db_path)

def test_mark_synced():
    fd, db_path = tempfile.mkstemp()
    os.close(fd)
    try:
        buf = SqliteBuffer(db_path)
        buf.insert_event({'event_id': '1'})
        buf.insert_event({'event_id': '2'})
        
        buf.mark_synced(['1'])
        stats = buf.get_stats()
        assert stats['pending'] == 1
        assert stats['synced'] == 1
    finally:
        os.unlink(db_path)
