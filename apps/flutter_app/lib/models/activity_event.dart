class ActivityEvent {
  final String id;
  final String? appName;
  final String? browserDomain;
  final String? titleCategory;
  final String? activityCategory;
  final String? classificationSource;
  final double? confidence;
  final int durationSeconds;

  ActivityEvent({
    required this.id, this.appName, this.browserDomain, 
    this.titleCategory, this.activityCategory, this.classificationSource, this.confidence, 
    required this.durationSeconds,
  });

  factory ActivityEvent.fromJson(Map<String, dynamic> json) {
    return ActivityEvent(
      id: json['event_id'] ?? json['id'] ?? 'unknown',
      appName: json['app_name'] ?? json['appName'],
      browserDomain: json['browser_domain'] ?? json['browserDomain'],
      titleCategory: json['browser_tab_title_category'] ?? json['browserTabTitleCategory'],
      activityCategory: json['activity_category'] ?? json['activityCategory'],
      classificationSource: json['classification_source'] ?? json['classificationSource'],
      confidence: (json['classification_confidence'] ?? json['classificationConfidence'])?.toDouble(),
      durationSeconds: json['duration_seconds'] ?? json['durationSeconds'] ?? 0,
    );
  }
}
