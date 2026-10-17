import unittest
from unittest.mock import patch, MagicMock
from tracker import WindowsTracker
import datetime

class TestWindowsTracker(unittest.TestCase):
    @patch('tracker.requests.post')
    def test_authentication_and_device_registration(self, mock_post):
        mock_login_res = MagicMock()
        mock_login_res.status_code = 200
        mock_login_res.json.return_value = {'token': 'fake_token'}
        
        mock_dev_res = MagicMock()
        mock_dev_res.status_code = 200
        mock_dev_res.json.return_value = {'id': 'fake_device_id'}
        
        mock_post.side_effect = [mock_login_res, mock_dev_res]
        
        tracker = WindowsTracker("test@example.com", "password")
        tracker.authenticate()
        
        self.assertEqual(tracker.token, 'fake_token')
        self.assertEqual(tracker.device_id, 'fake_device_id')

    @patch('tracker.requests.post')
    def test_sync_event(self, mock_post):
        tracker = WindowsTracker("test@example.com", "password")
        tracker.token = 'fake_token'
        tracker.device_id = 'fake_device_id'
        
        mock_sync_res = MagicMock()
        mock_sync_res.status_code = 202
        mock_post.return_value = mock_sync_res
        
        start_time = datetime.datetime.now(datetime.timezone.utc)
        
        # Test ignoring short sessions (< 5s)
        tracker.sync_event("code.exe", "test - VS Code", start_time, 2, 0, 0)
        self.assertEqual(mock_post.call_count, 0)
        
        # Test syncing valid sessions
        tracker.sync_event("code.exe", "test - VS Code", start_time, 300, 10, 5)
        self.assertEqual(mock_post.call_count, 1)
        
        payload = mock_post.call_args[1]['json']['events'][0]
        self.assertEqual(payload['appName'], 'code.exe')
        self.assertEqual(payload['durationSeconds'], 300)
        self.assertEqual(payload['idleSeconds'], 10)
        self.assertEqual(payload['appSwitchCount'], 5)

    def test_get_idle_time_live(self):
        tracker = WindowsTracker("test@example.com", "password")
        idle_time = tracker.get_idle_time()
        self.assertIsInstance(idle_time, float)
        self.assertGreaterEqual(idle_time, 0.0)

    def test_get_idle_time_normal(self):
        tracker = WindowsTracker("test@example.com", "password")
        
        # Mock GetLastInputInfo to populate dwTime
        def fake_get_last_input_info(byref_lii):
            lii = byref_lii._obj
            lii.dwTime = 100000  # 100 seconds in ms
            return True

        tracker.user32.GetLastInputInfo = MagicMock(side_effect=fake_get_last_input_info)
        tracker.kernel32.GetTickCount64 = MagicMock(return_value=105500)  # 105.5 seconds in ms

        idle = tracker.get_idle_time()
        self.assertAlmostEqual(idle, 5.5, places=3)

    def test_get_idle_time_wraparound(self):
        tracker = WindowsTracker("test@example.com", "password")
        
        # Mock dwTime near 32-bit max (e.g. 4294967200, which is 0xFFFFFF80)
        def fake_get_last_input_info(byref_lii):
            lii = byref_lii._obj
            lii.dwTime = 4294967200
            return True

        tracker.user32.GetLastInputInfo = MagicMock(side_effect=fake_get_last_input_info)
        # Mock GetTickCount64 wrapped around to 100 ms after 32-bit boundary
        tracker.kernel32.GetTickCount64 = MagicMock(return_value=0x100000000 + 100)

        idle = tracker.get_idle_time()
        # Expected: (100 - (4294967200 - 4294967296)) = 100 + 96 = 196 ms = 0.196 s
        self.assertAlmostEqual(idle, 0.196, places=3)

    def test_get_idle_time_jitter(self):
        tracker = WindowsTracker("test@example.com", "password")
        
        # dwTime sampled slightly ahead of tick count due to thread scheduling
        def fake_get_last_input_info(byref_lii):
            lii = byref_lii._obj
            lii.dwTime = 100050
            return True

        tracker.user32.GetLastInputInfo = MagicMock(side_effect=fake_get_last_input_info)
        tracker.kernel32.GetTickCount64 = MagicMock(return_value=100000)

        idle = tracker.get_idle_time()
        self.assertEqual(idle, 0.0)

    def test_get_idle_time_api_failure(self):
        tracker = WindowsTracker("test@example.com", "password")
        tracker.user32.GetLastInputInfo = MagicMock(return_value=False)
        idle = tracker.get_idle_time()
        self.assertEqual(idle, 0.0)

    @patch('tracker.requests.get')
    def test_device_id_reuse_from_valid_session(self, mock_get):
        import tempfile
        with tempfile.TemporaryDirectory() as tmpdir:
            mock_dev_res = MagicMock()
            mock_dev_res.status_code = 200
            mock_dev_res.json.return_value = [{'id': 'existing_device_123', 'platform': 'windows'}]
            mock_get.return_value = mock_dev_res

            tracker = WindowsTracker("test@example.com", config_dir=tmpdir)
            tracker.token = 'cached_token'
            tracker.device_id = 'existing_device_123'
            tracker.config = {'email': 'test@example.com', 'token': 'cached_token', 'device_id': 'existing_device_123'}

            # Authenticate should verify via GET and reuse device without POSTing new registration
            tracker.authenticate()
            self.assertEqual(tracker.device_id, 'existing_device_123')
            self.assertEqual(mock_get.call_count, 1)

if __name__ == '__main__':
    unittest.main()
