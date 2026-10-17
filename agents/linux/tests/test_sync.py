import responses
from sync.api_client import FlowStateApiClient

@responses.activate
def test_sync_events_success():
    api = FlowStateApiClient('http://localhost', 'token', 'dev1')
    responses.add(
        responses.POST,
        'http://localhost/api/events',
        json={'accepted': 1, 'duplicates_skipped': 0, 'errors': []},
        status=202
    )
    
    res = api.sync_events([{'event_id': '1'}])
    assert res.success
    assert res.accepted == 1

@responses.activate
def test_sync_events_failure():
    api = FlowStateApiClient('http://localhost', 'token', 'dev1')
    responses.add(
        responses.POST,
        'http://localhost/api/events',
        status=500
    )
    
    res = api.sync_events([{'event_id': '1'}])
    assert not res.success
