class FeaturePipeline:
    def process(self, raw_request: dict) -> dict:
        features = {}
        
        # app category
        app_cat = raw_request.get('app_identifier', '')
        from classifier.known_apps import KNOWN_APP_CATEGORIES
        features['app_category_encoded'] = hash(KNOWN_APP_CATEGORIES.get(app_cat, 'unknown')) % 10
        
        # domain category
        domain = raw_request.get('browser_domain', '')
        from classifier.known_domains import KNOWN_DOMAIN_CATEGORIES
        domain_info = KNOWN_DOMAIN_CATEGORIES.get(domain, ('unknown', True))
        features['domain_category_encoded'] = hash(domain_info[0]) % 10
        features['domain_is_ambiguous'] = 1 if domain_info[1] else 0
        
        # duration
        duration = raw_request.get('duration_seconds', 0)
        features['duration_bucket'] = self.get_duration_bucket(duration)
        
        # idle ratio
        idle = raw_request.get('idle_seconds')
        if idle is None or duration == 0:
            features['idle_ratio'] = 0.5
        else:
            features['idle_ratio'] = min(1.0, idle / duration)
            
        # switch rate
        switches = raw_request.get('app_switch_count')
        if switches is None or duration == 0:
            features['switch_rate_per_minute'] = 0.0
        else:
            features['switch_rate_per_minute'] = switches / (duration / 60.0)
            
        # time of day
        features['time_of_day_encoded'] = hash(raw_request.get('time_of_day_slot', '')) % 5
        features['day_type'] = 1 if raw_request.get('day_of_week') in ['saturday', 'sunday'] else 0
        
        # focus
        features['is_focus_session_active'] = 1 if raw_request.get('is_focus_session_active') else 0
        
        # goal
        features['goal_category_encoded'] = hash(raw_request.get('declared_goal_category', '')) % 5
        
        return features
        
    def get_duration_bucket(self, seconds: int) -> int:
        if seconds < 30: return 0
        if seconds < 300: return 1
        if seconds < 1800: return 2
        if seconds < 7200: return 3
        return 4
