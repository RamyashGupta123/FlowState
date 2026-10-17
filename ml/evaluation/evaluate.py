from sklearn.metrics import classification_report

def evaluate_classifier(model, X_test, y_test, label_names=None):
    y_pred = model.predict(X_test)
    report = classification_report(y_test, y_pred, output_dict=True, zero_division=0)
    print(classification_report(y_test, y_pred, zero_division=0))
    return report
