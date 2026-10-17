from classifier.feature_pipeline import FeaturePipeline

def test_duration_bucket():
    pipe = FeaturePipeline()
    assert pipe.get_duration_bucket(10) == 0
    assert pipe.get_duration_bucket(60) == 1
    assert pipe.get_duration_bucket(600) == 2
    assert pipe.get_duration_bucket(3600) == 3
    assert pipe.get_duration_bucket(8000) == 4

def test_process():
    pipe = FeaturePipeline()
    raw = {
        'duration_seconds': 60,
        'idle_seconds': 6,
        'app_switch_count': 2,
        'app_identifier': 'code'
    }
    features = pipe.process(raw)
    assert features['duration_bucket'] == 1
    assert features['idle_ratio'] == 0.1
    assert features['switch_rate_per_minute'] == 2.0
