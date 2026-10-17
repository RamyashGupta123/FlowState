import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../core/auth_manager.dart';
import '../core/api_client.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  bool _focusReminders = true;
  bool _breakReminders = true;
  bool _trackingConsent = true;
  bool _loading = true;
  bool _exporting = false;

  @override
  void initState() {
    super.initState();
    _loadPreferences();
  }

  Future<void> _loadPreferences() async {
    final prefs = await SharedPreferences.getInstance();
    bool focus = prefs.getBool('pref_focus_reminders') ?? true;
    bool breaks = prefs.getBool('pref_break_reminders') ?? true;
    bool consent = prefs.getBool('pref_tracking_consent') ?? true;

    try {
      final res = await ApiClient.get('/recommendations/preferences');
      if (res.statusCode == 200) {
        final data = jsonDecode(res.body) as Map<String, dynamic>;
        if (data['focusRemindersEnabled'] != null) focus = data['focusRemindersEnabled'] == true;
        if (data['breakRemindersEnabled'] != null) breaks = data['breakRemindersEnabled'] == true;
      }
    } catch (_) {}

    setState(() {
      _focusReminders = focus;
      _breakReminders = breaks;
      _trackingConsent = consent;
      _loading = false;
    });
  }

  Future<void> _updatePref(String key, bool value, Function(bool) updater) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(key, value);
    setState(() => updater(value));

    try {
      if (key == 'pref_focus_reminders') {
        await ApiClient.put('/recommendations/preferences', {'focusRemindersEnabled': value});
      } else if (key == 'pref_break_reminders') {
        await ApiClient.put('/recommendations/preferences', {'breakRemindersEnabled': value});
      }
    } catch (_) {}
  }

  Future<void> _exportUserData() async {
    setState(() => _exporting = true);
    try {
      final res = await ApiClient.get('/user/export');
      if (res.statusCode == 200) {
        final data = jsonDecode(res.body) as Map<String, dynamic>;
        if (mounted) {
          _showExportDialog(data);
        }
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to export data: ${res.body}')),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Export error: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _exporting = false);
    }
  }

  void _showExportDialog(Map<String, dynamic> data) {
    final userProfile = data['user_profile'] as Map<String, dynamic>? ?? {};
    final devices = data['devices'] as List<dynamic>? ?? [];
    final sessions = data['focus_sessions'] as List<dynamic>? ?? [];
    final events = data['activity_events'] as List<dynamic>? ?? [];
    final exportedAt = data['exported_at']?.toString() ?? 'Just now';

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Row(
          children: [
            Icon(Icons.download_done, color: Colors.green),
            SizedBox(width: 8),
            Text('Data Export (GDPR/CCPA)'),
          ],
        ),
        content: SizedBox(
          width: 450,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Export generated on $exportedAt', style: const TextStyle(fontWeight: FontWeight.bold)),
              const SizedBox(height: 12),
              _buildExportRow('Account Email:', userProfile['email']?.toString() ?? 'N/A'),
              _buildExportRow('Registered Devices:', '${devices.length}'),
              _buildExportRow('Focus Sessions:', '${sessions.length}'),
              _buildExportRow('Activity Events:', '${events.length}'),
              const Divider(height: 24),
              const Text(
                'Security & Privacy Guarantee:\n'
                '• All events and sessions associated with your account are included.\n'
                '• Passwords and credential hashes are strictly excluded.\n'
                '• No keystrokes or screen captures are ever recorded.',
                style: TextStyle(fontSize: 12, color: Colors.grey),
              ),
            ],
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }

  Widget _buildExportRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Colors.black87)),
          Text(value, style: const TextStyle(fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }

  void _showPrivacyInfo() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Privacy & Tracking Policy'),
        content: const SingleChildScrollView(
          child: Text(
            'FlowState is engineered for privacy-first wellbeing tracking:\n\n'
            '1. Desktop Agent (Windows):\n'
            '   • Only tracks active foreground window process name, permitted title, and duration.\n'
            '   • Does NOT record keystrokes, screen captures, or background applications.\n'
            '   • Pauses automatically when user is idle or screen is locked.\n\n'
            '2. Browser Extension (Chrome):\n'
            '   • Tracks active domain and duration.\n'
            '   • Cleans URLs to prevent capturing query params with sensitive tokens.\n\n'
            '3. On-Device & Server Storage:\n'
            '   • Uses encrypted authentication tokens.\n'
            '   • Offline events are queued in local cache until connected to backend.',
          ),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Got it')),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    return ListView(
      padding: const EdgeInsets.all(16.0),
      children: [
        Text('Settings', style: Theme.of(context).textTheme.headlineSmall),
        const SizedBox(height: 16),
        SwitchListTile(
          title: const Text('Focus Reminders'),
          subtitle: const Text('Receive notifications when focus sessions start or pause'),
          value: _focusReminders,
          onChanged: (v) => _updatePref('pref_focus_reminders', v, (val) => _focusReminders = val),
        ),
        SwitchListTile(
          title: const Text('Break Reminders'),
          subtitle: const Text('Periodic reminders to stretch and rest eyes after prolonged focus'),
          value: _breakReminders,
          onChanged: (v) => _updatePref('pref_break_reminders', v, (val) => _breakReminders = val),
        ),
        SwitchListTile(
          title: const Text('Tracking Consent'),
          subtitle: const Text('Permit agents to collect app duration metrics locally'),
          value: _trackingConsent,
          onChanged: (v) => _updatePref('pref_tracking_consent', v, (val) => _trackingConsent = val),
        ),
        const Divider(height: 24),
        ListTile(
          leading: const Icon(Icons.shield_outlined, color: Colors.blue),
          title: const Text('Privacy & Consent Details'),
          subtitle: const Text('Review what metrics are tracked and protected'),
          trailing: const Icon(Icons.chevron_right),
          onTap: _showPrivacyInfo,
        ),
        ListTile(
          leading: _exporting
              ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2))
              : const Icon(Icons.download, color: Colors.green),
          title: const Text('Data Export (JSON)'),
          subtitle: const Text('Export all account data, sessions, and activity records'),
          trailing: const Icon(Icons.chevron_right),
          onTap: _exporting ? null : _exportUserData,
        ),
        const Divider(height: 32),
        ListTile(
          leading: const Icon(Icons.logout, color: Colors.red),
          title: const Text('Logout', style: TextStyle(color: Colors.red, fontWeight: FontWeight.bold)),
          onTap: () {
            context.read<AuthManager>().logout();
          },
        ),
      ],
    );
  }
}
