import 'dart:convert';
import 'package:flutter/material.dart';
import '../core/api_client.dart';
import '../models/activity_event.dart';

class TimelineScreen extends StatefulWidget {
  const TimelineScreen({super.key});
  @override
  State<TimelineScreen> createState() => _TimelineScreenState();
}

class _TimelineScreenState extends State<TimelineScreen> {
  List<ActivityEvent> _events = [];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _loadEvents();
  }

  Future<void> _loadEvents() async {
    try {
      final res = await ApiClient.get('/events?page=0&size=50');
      if (res.statusCode == 200) {
        final data = jsonDecode(res.body);
        setState(() {
          _events = (data['events'] as List).map((e) => ActivityEvent.fromJson(e)).toList();
        });
      }
    } finally {
      setState(() => _loading = false);
    }
  }

  Future<void> _correctCategory(ActivityEvent event, String newCategory) async {
    final res = await ApiClient.post('/events/${event.id}/feedback', {
      'corrected_category': newCategory
    });
    if (res.statusCode == 200) {
      _loadEvents(); // Reload
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_events.isEmpty) return const Center(child: Text('No events found.'));

    return RefreshIndicator(
      onRefresh: _loadEvents,
      child: ListView.builder(
        itemCount: _events.length,
        itemBuilder: (context, index) {
          final e = _events[index];
          
          String categoryDisplay;
          if (e.activityCategory != null) {
            categoryDisplay = e.activityCategory!;
          } else if (e.classificationSource != null) {
            final source = e.classificationSource!;
            categoryDisplay = source[0].toUpperCase() + source.substring(1); // capitalize
          } else {
            categoryDisplay = 'Pending/Unknown';
          }

          return Card(
            margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: ListTile(
              title: Text(e.appName ?? e.browserDomain ?? 'Unknown'),
              subtitle: Text('Category: $categoryDisplay (${e.durationSeconds}s)'),
              trailing: PopupMenuButton<String>(
                onSelected: (val) => _correctCategory(e, val),
                itemBuilder: (context) => const [
                  PopupMenuItem(value: 'productive_work', child: Text('Productive')),
                  PopupMenuItem(value: 'potential_distraction', child: Text('Distracting')),
                  PopupMenuItem(value: 'intentional_leisure', child: Text('Leisure')),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}
