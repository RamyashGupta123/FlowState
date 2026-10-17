"""SYNTHETIC DEVELOPMENT DATA — Not real user activity.
This data is used only for initial model training and evaluation during development.
Do not report accuracy metrics from this data as representative of real-world performance."""

import pandas as pd
import numpy as np

def generate_synthetic_dataset(n_samples=1000):
    np.random.seed(42)
    categories = [
        'productive_work', 'learning_research', 'communication_admin',
        'intentional_leisure', 'break_idle', 'potential_distraction', 'unknown'
    ]
    
    data = {
        'app_category_encoded': np.random.randint(0, 10, n_samples),
        'domain_category_encoded': np.random.randint(0, 10, n_samples),
        'domain_is_ambiguous': np.random.randint(0, 2, n_samples),
        'duration_bucket': np.random.randint(0, 5, n_samples),
        'idle_ratio': np.random.uniform(0, 1, n_samples),
        'switch_rate_per_minute': np.random.exponential(1.0, n_samples),
        'time_of_day_encoded': np.random.randint(0, 5, n_samples),
        'day_type': np.random.randint(0, 2, n_samples),
        'is_focus_session_active': np.random.randint(0, 2, n_samples),
        'goal_category_encoded': np.random.randint(0, 5, n_samples),
        'category': np.random.choice(categories, n_samples)
    }
    
    return pd.DataFrame(data)
