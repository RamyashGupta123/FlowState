from dataclasses import dataclass
import joblib
import numpy as np

@dataclass
class ClassificationResult:
    predicted_category: str
    confidence: float
    goal_alignment: str
    evidence: list[str]
    requires_user_confirmation: bool
    classification_source: str

class FlowStateClassifier:
    def __init__(self):
        self.model = None

    def load_model(self, path: str):
        try:
            self.model = joblib.load(path)
        except Exception as e:
            print(f"Failed to load model: {e}")

    def is_model_loaded(self) -> bool:
        return self.model is not None

    def _determine_goal_alignment(self, predicted: str, target: str, is_focus: bool) -> str:
        if not target:
            return 'unknown'
        if predicted == target:
            return 'aligned'
        if predicted == 'potential_distraction' and is_focus:
            return 'misaligned'
        return 'neutral'

    def rule_based_classify(self, features: dict, raw_request: dict) -> ClassificationResult:
        evidence = []
        requires_confirmation = False
        category = 'unknown'
        confidence = 0.40

        is_focus = features.get('is_focus_session_active', 0) == 1
        idle_ratio = features.get('idle_ratio', 0)
        app_raw = raw_request.get('app_identifier', '') or ''
        app_clean = app_raw.lower().strip().replace('.exe', '')
        domain_raw = raw_request.get('browser_domain', '') or ''
        domain_clean = domain_raw.lower().strip()
        duration = raw_request.get('duration_seconds', 0)
        platform = raw_request.get('platform', '')
        
        from classifier.known_apps import KNOWN_APP_CATEGORIES
        from classifier.known_domains import KNOWN_DOMAIN_CATEGORIES

        app_category = KNOWN_APP_CATEGORIES.get(app_clean, 'unknown')
        
        # Domain resolution (exact match or keyword match in title/domain)
        domain_cat = 'unknown'
        domain_ambiguous = True
        if domain_clean in KNOWN_DOMAIN_CATEGORIES:
            domain_cat, domain_ambiguous = KNOWN_DOMAIN_CATEGORIES[domain_clean]
        elif domain_clean:
            # Strip browser name suffix to avoid false positives (e.g. ' - Google Chrome' matching 'google.com')
            title_body = domain_clean
            for suffix in [' - google chrome', ' - microsoft edge', ' - mozilla firefox', ' - brave', ' - opera']:
                if title_body.endswith(suffix):
                    title_body = title_body[:-len(suffix)].strip()

            SPECIFIC_SERVICE_KEYWORDS = {
                'github': ('development', False),
                'gitlab': ('development', False),
                'stackoverflow': ('learning_research', False),
                'youtube': ('video_streaming', True),
                'chatgpt': ('productivity', False),
                'openai': ('productivity', False),
                'claude': ('productivity', False),
                'reddit': ('social', True),
                'netflix': ('entertainment', False),
                'twitch': ('entertainment', True),
                'slack': ('communication', False),
                'notion': ('productivity', False),
                'jira': ('productivity', False),
                'confluence': ('productivity', False),
                'trello': ('productivity', False),
                'localhost': ('development', False),
                '127.0.0.1': ('development', False),
            }

            for keyword, (k_cat, k_ambig) in SPECIFIC_SERVICE_KEYWORDS.items():
                if keyword in title_body:
                    domain_cat, domain_ambiguous = k_cat, k_ambig
                    break

        # 1. Idle detection
        if idle_ratio > 0.9:
            category = 'break_idle'
            confidence = 0.95
            evidence.append("Idle ratio > 0.9")

        # 2. Focus session rules for known apps / domains
        elif (app_category in ['development', 'productivity', 'office'] or domain_cat in ['development', 'productivity']) and is_focus:
            category = 'productive_work'
            confidence = 0.88
            evidence.append(f"Productive activity during focus session ({app_clean or domain_clean})")
        elif (app_category in ['entertainment', 'gaming'] or domain_cat in ['entertainment', 'social', 'video_streaming']) and is_focus:
            category = 'potential_distraction'
            confidence = 0.80
            evidence.append(f"Distracting activity during focus session ({app_clean or domain_clean})")

        # 3. Known desktop applications (non-browser)
        elif app_category != 'unknown' and app_category != 'browser':
            category = app_category
            confidence = 0.80
            evidence.append(f"Mapped application '{app_clean}' to {app_category} via rules")

        # 4. Browser activity (either browser app or browser platform)
        elif app_category == 'browser' or platform == 'browser':
            if domain_cat != 'unknown':
                category = domain_cat
                confidence = 0.80 if not domain_ambiguous else 0.65
                evidence.append(f"Browser activity mapped to {domain_cat} via domain/title")
            elif domain_clean and domain_ambiguous and duration < 300:
                category = 'unknown'
                confidence = 0.40
                requires_confirmation = True
                evidence.append("Ambiguous browser activity and short duration")
            else:
                category = 'unknown'
                confidence = 0.40
                requires_confirmation = True
                evidence.append("Unclassified browser activity")

        # 5. Direct domain fallback if domain_cat is known
        elif domain_cat != 'unknown':
            category = domain_cat
            confidence = 0.70
            evidence.append(f"Mapped to {domain_cat} via domain rules")

        # 6. Unrecognized
        else:
            category = 'unknown'
            confidence = 0.40
            requires_confirmation = True
            evidence.append(f"Unrecognized activity: app='{app_clean}', domain='{domain_clean}'")

        return ClassificationResult(
            predicted_category=category,
            confidence=confidence,
            goal_alignment=self._determine_goal_alignment(category, raw_request.get('declared_goal_category'), is_focus),
            evidence=evidence,
            requires_user_confirmation=requires_confirmation,
            classification_source='rules'
        )

    def ml_classify(self, features: dict, raw_request: dict) -> ClassificationResult:
        if not self.is_model_loaded():
            return self.rule_based_classify(features, raw_request)

        feature_vector = np.array([[
            features['app_category_encoded'],
            features['domain_category_encoded'],
            features['domain_is_ambiguous'],
            features['duration_bucket'],
            features['idle_ratio'],
            features['switch_rate_per_minute'],
            features['time_of_day_encoded'],
            features['day_type'],
            features['is_focus_session_active'],
            features['goal_category_encoded']
        ]])

        probs = self.model.predict_proba(feature_vector)[0]
        max_idx = np.argmax(probs)
        confidence = float(probs[max_idx])
        predicted_category = self.model.classes_[max_idx]

        if confidence < 0.60:
            return self.rule_based_classify(features, raw_request)

        is_focus = features.get('is_focus_session_active', 0) == 1
        requires_confirmation = confidence < 0.75

        return ClassificationResult(
            predicted_category=predicted_category,
            confidence=confidence,
            goal_alignment=self._determine_goal_alignment(predicted_category, raw_request.get('declared_goal_category'), is_focus),
            evidence=[f"ML Model confidence: {confidence:.2f}"],
            requires_user_confirmation=requires_confirmation,
            classification_source='ml_model'
        )

    def classify(self, features: dict, raw_request: dict) -> ClassificationResult:
        if self.is_model_loaded():
            return self.ml_classify(features, raw_request)
        return self.rule_based_classify(features, raw_request)
