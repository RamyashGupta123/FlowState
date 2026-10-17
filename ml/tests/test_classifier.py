from classifier.classifier import FlowStateClassifier

def test_rule_based():
    clf = FlowStateClassifier()
    features = {'is_focus_session_active': 0, 'idle_ratio': 0.95}
    raw = {}
    res = clf.rule_based_classify(features, raw)
    assert res.predicted_category == 'break_idle'
    
    features = {'is_focus_session_active': 1, 'idle_ratio': 0.1}
    raw = {'app_identifier': 'code'}
    res = clf.rule_based_classify(features, raw)
    assert res.predicted_category == 'productive_work'

def test_goal_alignment():
    clf = FlowStateClassifier()
    assert clf._determine_goal_alignment('productive_work', 'productive_work', True) == 'aligned'
    assert clf._determine_goal_alignment('potential_distraction', 'productive_work', True) == 'misaligned'
    assert clf._determine_goal_alignment('learning_research', 'productive_work', True) == 'neutral'
    assert clf._determine_goal_alignment('productive_work', None, True) == 'unknown'

def test_windows_applications():
    clf = FlowStateClassifier()
    
    # 1. VS Code maps to development
    res_code = clf.rule_based_classify(
        {'is_focus_session_active': 0, 'idle_ratio': 0.05},
        {'platform': 'windows', 'app_identifier': 'code', 'duration_seconds': 45}
    )
    assert res_code.predicted_category == 'development'
    assert res_code.confidence >= 0.8
    
    # 2. Windows Terminal maps to development
    res_term = clf.rule_based_classify(
        {'is_focus_session_active': 0, 'idle_ratio': 0.05},
        {'platform': 'windows', 'app_identifier': 'windowsterminal', 'duration_seconds': 60}
    )
    assert res_term.predicted_category == 'development'
    
    # 3. Notepad maps to productivity
    res_notepad = clf.rule_based_classify(
        {'is_focus_session_active': 0, 'idle_ratio': 0.05},
        {'platform': 'windows', 'app_identifier': 'notepad', 'duration_seconds': 120}
    )
    assert res_notepad.predicted_category == 'productivity'
    
    # 4. Chrome with ambiguous title does not default to productive
    res_chrome_ambig = clf.rule_based_classify(
        {'is_focus_session_active': 0, 'idle_ratio': 0.05},
        {'platform': 'windows', 'app_identifier': 'chrome', 'browser_domain': 'Random Blog Post - Google Chrome', 'duration_seconds': 60}
    )
    assert res_chrome_ambig.predicted_category == 'unknown'
    
    # 5. Chrome with identifiable GitHub title maps to development
    res_chrome_gh = clf.rule_based_classify(
        {'is_focus_session_active': 0, 'idle_ratio': 0.05},
        {'platform': 'windows', 'app_identifier': 'chrome', 'browser_domain': 'github.com', 'duration_seconds': 90}
    )
    assert res_chrome_gh.predicted_category == 'development'
    
    # 6. Chrome with identifiable YouTube title maps to video_streaming
    res_chrome_yt = clf.rule_based_classify(
        {'is_focus_session_active': 0, 'idle_ratio': 0.05},
        {'platform': 'windows', 'app_identifier': 'chrome', 'browser_domain': 'youtube.com', 'duration_seconds': 90}
    )
    assert res_chrome_yt.predicted_category == 'video_streaming'
