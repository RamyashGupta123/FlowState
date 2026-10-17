from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
import joblib
import os
import sys

# Ensure modules can be imported
sys.path.append(os.path.dirname(os.path.dirname(__file__)))

from data_processing.synthetic_data import generate_synthetic_dataset
from evaluation.evaluate import evaluate_classifier

def train_and_save():
    print("WARNING: Training on synthetic data. Metrics do not reflect real-world performance.")
    df = generate_synthetic_dataset(1000)
    print(df['category'].value_counts())
    
    X = df.drop(columns=['category'])
    y = df['category']
    
    X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)
    
    clf = RandomForestClassifier(n_estimators=100, random_state=42)
    clf.fit(X_train, y_train)
    
    evaluate_classifier(clf, X_test, y_test)
    
    os.makedirs('models', exist_ok=True)
    joblib.dump(clf, 'models/classifier.joblib')
    print("Model saved to models/classifier.joblib")

if __name__ == '__main__':
    train_and_save()
