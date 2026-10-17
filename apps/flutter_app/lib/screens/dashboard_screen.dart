import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../core/auth_manager.dart';
import '../core/api_client.dart';
import 'timeline_screen.dart';
import 'settings_screen.dart';
import '../widgets/stat_card.dart';

class DashboardScreen extends StatefulWidget {
  const DashboardScreen({super.key});
  @override
  State<DashboardScreen> createState() => _DashboardScreenState();
}

class _DashboardScreenState extends State<DashboardScreen> {
  int _currentIndex = 0;
  Map<String, dynamic>? _summary;
  bool _loading = true;

  // Focus Session State
  Map<String, dynamic>? _activeSession;
  Timer? _timer;
  int _elapsedSeconds = 0;
  int _selectedDurationMinutes = 25;
  final TextEditingController _notesController = TextEditingController();
  bool _sessionActionLoading = false;

  // Recommendation State
  Map<String, dynamic>? _recommendationResult;
  bool _recommendationLoading = false;
  String? _currentDemoScenario;

  @override
  void initState() {
    super.initState();
    _loadSummary();
    _loadActiveSession();
    _loadRecommendation();
  }

  Future<void> _loadRecommendation({String? demoScenario}) async {
    setState(() => _recommendationLoading = true);
    try {
      final endpoint = demoScenario != null && demoScenario.isNotEmpty
          ? '/recommendations/current?demo_scenario=$demoScenario'
          : '/recommendations/current';
      final res = await ApiClient.get(endpoint);
      if (res.statusCode == 200) {
        if (mounted) {
          setState(() {
            _recommendationResult = jsonDecode(res.body);
            _currentDemoScenario = demoScenario;
          });
        }
      }
    } catch (_) {
    } finally {
      if (mounted) setState(() => _recommendationLoading = false);
    }
  }

  Future<void> _submitRecommendationFeedback(String recId, String feedbackType) async {
    setState(() => _recommendationLoading = true);
    try {
      final res = await ApiClient.post('/recommendations/$recId/feedback', {
        'feedbackType': feedbackType,
      });
      if (res.statusCode == 200) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('Feedback saved: $feedbackType'),
              duration: const Duration(seconds: 2),
            ),
          );
        }
        await _loadRecommendation(demoScenario: _currentDemoScenario);
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _recommendationLoading = false);
    }
  }

  @override
  void dispose() {
    _timer?.cancel();
    _notesController.dispose();
    super.dispose();
  }

  Future<void> _loadActiveSession() async {
    try {
      var res = await ApiClient.get('/sessions?status=active');
      if (res.statusCode == 200) {
        final List<dynamic> activeList = jsonDecode(res.body);
        if (activeList.isNotEmpty) {
          _setSession(activeList.first as Map<String, dynamic>, isActive: true);
          return;
        }
      }

      res = await ApiClient.get('/sessions?status=paused');
      if (res.statusCode == 200) {
        final List<dynamic> pausedList = jsonDecode(res.body);
        if (pausedList.isNotEmpty) {
          _setSession(pausedList.first as Map<String, dynamic>, isActive: false);
          return;
        }
      }

      if (mounted) {
        setState(() {
          _activeSession = null;
          _stopTimer();
        });
      }
    } catch (_) {}
  }

  void _setSession(Map<String, dynamic> session, {required bool isActive}) {
    if (!mounted) return;
    int elapsed = 0;
    if (session['startTime'] != null) {
      final start = DateTime.tryParse(session['startTime'].toString());
      if (start != null) {
        elapsed = DateTime.now().toUtc().difference(start.toUtc()).inSeconds;
        if (elapsed < 0) elapsed = 0;
      }
    }
    setState(() {
      _activeSession = session;
      _elapsedSeconds = elapsed;
    });

    if (isActive) {
      _startTimer();
    } else {
      _stopTimer();
    }
  }

  void _startTimer() {
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (mounted) {
        setState(() => _elapsedSeconds++);
      }
    });
  }

  void _stopTimer() {
    _timer?.cancel();
    _timer = null;
  }

  Future<void> _startFocusSession() async {
    setState(() => _sessionActionLoading = true);
    try {
      final res = await ApiClient.post('/sessions', {
        'notes': _notesController.text.trim().isEmpty ? null : _notesController.text.trim(),
        'target_duration_minutes': _selectedDurationMinutes,
      });
      if (res.statusCode == 201) {
        final session = jsonDecode(res.body) as Map<String, dynamic>;
        _notesController.clear();
        _setSession(session, isActive: true);
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Failed to start session: ${res.body}')),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _sessionActionLoading = false);
    }
  }

  Future<void> _updateSessionAction(String action) async {
    if (_activeSession == null) return;
    setState(() => _sessionActionLoading = true);
    try {
      final id = _activeSession!['id'];
      final res = await ApiClient.put('/sessions/$id', {'action': action});
      if (res.statusCode == 200) {
        if (action == 'pause') {
          final updated = jsonDecode(res.body) as Map<String, dynamic>;
          _setSession(updated, isActive: false);
        } else if (action == 'resume') {
          final updated = jsonDecode(res.body) as Map<String, dynamic>;
          _setSession(updated, isActive: true);
        } else if (action == 'complete') {
          _stopTimer();
          setState(() {
            _activeSession = null;
            _elapsedSeconds = 0;
          });
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('Focus session completed! Great job! 🎉')),
            );
          }
          _loadSummary();
        } else if (action == 'abandon') {
          _stopTimer();
          setState(() {
            _activeSession = null;
            _elapsedSeconds = 0;
          });
        }
      } else {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('Action failed: ${res.body}')),
          );
        }
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('Error: $e')),
        );
      }
    } finally {
      if (mounted) setState(() => _sessionActionLoading = false);
    }
  }

  String _formatDuration(int seconds) {
    if (seconds <= 0) return '0 min';
    if (seconds < 60) return '${seconds}s';
    final mins = seconds ~/ 60;
    final rem = seconds % 60;
    if (mins < 10 && rem > 0) return '${mins}m ${rem}s';
    return '${(seconds / 60).round()} min';
  }

  Future<void> _loadSummary() async {
    try {
      final now = DateTime.now();
      final date = '${now.year.toString().padLeft(4, '0')}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
      final offset = now.timeZoneOffset;
      final hours = offset.inHours.abs().toString().padLeft(2, '0');
      final minutes = (offset.inMinutes % 60).abs().toString().padLeft(2, '0');
      final sign = offset.isNegative ? '-' : '+';
      final tz = '$sign$hours:$minutes';
      final encodedTz = Uri.encodeComponent(tz);
      
      final res = await ApiClient.get('/events/summary?date=$date&timezone=$encodedTz');
      if (res.statusCode == 200) {
        setState(() => _summary = jsonDecode(res.body));
      } else if (res.statusCode == 401 || res.statusCode == 403) {
        setState(() => _summary = {'error': 'Session expired. Please log in again.'});
      } else {
        setState(() => _summary = {'error': 'HTTP ${res.statusCode}: ${res.body}'});
      }
    } catch (e) {
      setState(() => _summary = {'error': e.toString()});
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Widget _buildRecommendationCard() {
    final theme = Theme.of(context);
    final result = _recommendationResult;
    final isEligible = result != null && result['eligible'] == true;
    final suppressionReason = result?['suppressionReason'];
    final explanation = result?['explanation'] ?? 'Evaluating focus state...';
    final rec = result?['recommendation'] as Map<String, dynamic>?;

    IconData iconData = Icons.spa;
    Color accentColor = Colors.teal;
    String interventionType = rec?['interventionType'] ?? '';

    if (interventionType == 'BREATHING_MINDFULNESS') {
      iconData = Icons.air;
      accentColor = Colors.teal;
    } else if (interventionType == 'WALK_STRETCH') {
      iconData = Icons.directions_walk;
      accentColor = Colors.green;
    } else if (interventionType == 'SCREEN_REST') {
      iconData = Icons.visibility;
      accentColor = Colors.indigo;
    } else if (interventionType == 'GENERAL_BREAK') {
      iconData = Icons.local_cafe;
      accentColor = Colors.amber.shade800;
    }

    return Card(
      elevation: 2,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(
          color: isEligible ? accentColor : Colors.grey.shade300,
          width: isEligible ? 2 : 1,
        ),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  isEligible ? iconData : Icons.shield_outlined,
                  color: isEligible ? accentColor : Colors.blueGrey,
                  size: 26,
                ),
                const SizedBox(width: 8),
                Text(
                  isEligible ? (rec?['title'] ?? 'Adaptive Recommendation') : 'FlowState Protection',
                  style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                ),
                const Spacer(),
                if (isEligible && rec?['durationSeconds'] != null)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: accentColor.withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      '~${(rec!['durationSeconds'] ~/ 60) > 0 ? '${rec['durationSeconds'] ~/ 60}m' : '${rec['durationSeconds']}s'}',
                      style: TextStyle(color: accentColor, fontWeight: FontWeight.bold, fontSize: 12),
                    ),
                  ),
                if (!isEligible && suppressionReason != null)
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: Colors.blueGrey.withValues(alpha: 0.12),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      suppressionReason,
                      style: const TextStyle(color: Colors.blueGrey, fontWeight: FontWeight.bold, fontSize: 11),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 10),
            if (isEligible && rec != null) ...[
              Text(
                rec['message'] ?? '',
                style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w500),
              ),
              const SizedBox(height: 6),
              Text(
                'Why: ${rec['reason'] ?? ''}',
                style: TextStyle(color: Colors.grey.shade600, fontSize: 12, fontStyle: FontStyle.italic),
              ),
              const SizedBox(height: 14),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  ElevatedButton.icon(
                    onPressed: _recommendationLoading
                        ? null
                        : () => _submitRecommendationFeedback(rec['id'], 'HELPFUL'),
                    icon: const Icon(Icons.thumb_up, size: 16),
                    label: const Text('Helpful'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.green.shade600,
                      foregroundColor: Colors.white,
                      visualDensity: VisualDensity.compact,
                    ),
                  ),
                  OutlinedButton.icon(
                    onPressed: _recommendationLoading
                        ? null
                        : () => _submitRecommendationFeedback(rec['id'], 'NOT_RELEVANT'),
                    icon: const Icon(Icons.thumb_down, size: 16),
                    label: const Text('Not Relevant'),
                    style: OutlinedButton.styleFrom(visualDensity: VisualDensity.compact),
                  ),
                  if (interventionType != 'WALK_STRETCH')
                    ActionChip(
                      avatar: const Icon(Icons.directions_walk, size: 16),
                      label: const Text('Prefer Walking'),
                      onPressed: _recommendationLoading
                          ? null
                          : () => _submitRecommendationFeedback(rec['id'], 'PREFER_WALKING'),
                    ),
                  if (interventionType != 'BREATHING_MINDFULNESS')
                    ActionChip(
                      avatar: const Icon(Icons.air, size: 16),
                      label: const Text('Prefer Breathing'),
                      onPressed: _recommendationLoading
                          ? null
                          : () => _submitRecommendationFeedback(rec['id'], 'PREFER_BREATHING'),
                    ),
                  TextButton.icon(
                    onPressed: _recommendationLoading
                        ? null
                        : () => _submitRecommendationFeedback(rec['id'], 'REMIND_ME_LATER'),
                    icon: const Icon(Icons.snooze, size: 16),
                    label: const Text('Remind in 15m'),
                  ),
                  TextButton(
                    onPressed: _recommendationLoading
                        ? null
                        : () => _submitRecommendationFeedback(rec['id'], 'DISMISS_FOR_TODAY'),
                    child: const Text('Dismiss Today', style: TextStyle(color: Colors.grey)),
                  ),
                ],
              ),
            ] else ...[
              Row(
                children: [
                  Icon(
                    suppressionReason == 'FOCUS_SESSION_ACTIVE'
                        ? Icons.lock_clock
                        : suppressionReason == 'RECENT_BREAK'
                            ? Icons.hourglass_top
                            : Icons.check_circle_outline,
                    size: 18,
                    color: Colors.blueGrey,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      explanation,
                      style: TextStyle(color: Colors.grey.shade700, fontSize: 13),
                    ),
                  ),
                ],
              ),
            ],
            const Divider(height: 24),
            // Interview Demo Controls (Expandable / discreet)
            Theme(
              data: theme.copyWith(dividerColor: Colors.transparent),
              child: ExpansionTile(
                tilePadding: EdgeInsets.zero,
                dense: true,
                title: Row(
                  children: [
                    Icon(Icons.science_outlined, size: 18, color: theme.colorScheme.primary),
                    const SizedBox(width: 6),
                    Text(
                      'Interview Demo Scenarios',
                      style: TextStyle(
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                        color: theme.colorScheme.primary,
                      ),
                    ),
                    const SizedBox(width: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: Colors.purple.shade50,
                        borderRadius: BorderRadius.circular(4),
                        border: Border.all(color: Colors.purple.shade200),
                      ),
                      child: const Text('Live Backend API', style: TextStyle(fontSize: 10, color: Colors.purple)),
                    ),
                  ],
                ),
                children: [
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      ElevatedButton.icon(
                        icon: const Icon(Icons.play_circle_outline, size: 16),
                        label: const Text('1. Trigger Work Reminder'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.indigo,
                          foregroundColor: Colors.white,
                          visualDensity: VisualDensity.compact,
                        ),
                        onPressed: () => _loadRecommendation(demoScenario: 'continuous_work'),
                      ),
                      OutlinedButton.icon(
                        icon: const Icon(Icons.shield, size: 16),
                        label: const Text('2. Test Focus Suppression'),
                        style: OutlinedButton.styleFrom(visualDensity: VisualDensity.compact),
                        onPressed: () => _loadRecommendation(demoScenario: 'focus_session_active'),
                      ),
                      OutlinedButton.icon(
                        icon: const Icon(Icons.coffee, size: 16),
                        label: const Text('3. Test Break Buffer'),
                        style: OutlinedButton.styleFrom(visualDensity: VisualDensity.compact),
                        onPressed: () => _loadRecommendation(demoScenario: 'recent_break'),
                      ),
                      OutlinedButton.icon(
                        icon: const Icon(Icons.refresh, size: 16),
                        label: const Text('Reset Demo State'),
                        style: OutlinedButton.styleFrom(visualDensity: VisualDensity.compact),
                        onPressed: () => _loadRecommendation(demoScenario: 'reset_demo'),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildFocusSessionCard() {
    final theme = Theme.of(context);
    final hasActiveSession = _activeSession != null;
    final isPaused = hasActiveSession && _activeSession!['status'] == 'paused';

    if (!hasActiveSession) {
      return Card(
        elevation: 2,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(Icons.self_improvement, color: theme.colorScheme.primary, size: 28),
                  const SizedBox(width: 8),
                  Text('Start a Focus Session', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                ],
              ),
              const SizedBox(height: 12),
              Text('Target Duration:', style: theme.textTheme.bodyMedium),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                children: [15, 25, 45, 60].map((minutes) {
                  return ChoiceChip(
                    label: Text('$minutes min'),
                    selected: _selectedDurationMinutes == minutes,
                    onSelected: (selected) {
                      if (selected) setState(() => _selectedDurationMinutes = minutes);
                    },
                  );
                }).toList(),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _notesController,
                decoration: const InputDecoration(
                  labelText: 'Session Goal / Notes (optional)',
                  hintText: 'e.g., Deep focus on architecture review',
                  border: OutlineInputBorder(),
                  isDense: true,
                ),
              ),
              const SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton.icon(
                  onPressed: _sessionActionLoading ? null : _startFocusSession,
                  icon: const Icon(Icons.play_arrow),
                  label: const Text('Start Focus Session'),
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    backgroundColor: theme.colorScheme.primary,
                    foregroundColor: Colors.white,
                  ),
                ),
              ),
            ],
          ),
        ),
      );
    }

    final minutes = (_elapsedSeconds ~/ 60).toString().padLeft(2, '0');
    final seconds = (_elapsedSeconds % 60).toString().padLeft(2, '0');
    final notes = _activeSession!['notes'];

    return Card(
      elevation: 3,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: isPaused ? Colors.amber : Colors.green, width: 2),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          children: [
            Row(
              children: [
                Icon(
                  isPaused ? Icons.pause_circle_outline : Icons.timer,
                  color: isPaused ? Colors.amber.shade800 : Colors.green,
                  size: 26,
                ),
                const SizedBox(width: 8),
                Text('Active Focus Session', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                const Spacer(),
                Chip(
                  label: Text(
                    isPaused ? 'Paused' : 'Active',
                    style: TextStyle(color: isPaused ? Colors.amber.shade900 : Colors.green.shade900, fontWeight: FontWeight.bold),
                  ),
                  backgroundColor: isPaused ? Colors.amber.shade100 : Colors.green.shade100,
                  visualDensity: VisualDensity.compact,
                ),
              ],
            ),
            const SizedBox(height: 12),
            Text(
              '$minutes:$seconds',
              style: TextStyle(
                fontSize: 42,
                fontWeight: FontWeight.bold,
                letterSpacing: 2,
                color: isPaused ? Colors.grey.shade700 : theme.colorScheme.primary,
              ),
            ),
            if (notes != null && notes.toString().trim().isNotEmpty) ...[
              const SizedBox(height: 6),
              Text(
                'Goal: $notes',
                style: TextStyle(fontStyle: FontStyle.italic, color: Colors.grey.shade700),
              ),
            ],
            const SizedBox(height: 16),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              alignment: WrapAlignment.center,
              children: [
                if (!isPaused)
                  ElevatedButton.icon(
                    onPressed: _sessionActionLoading ? null : () => _updateSessionAction('pause'),
                    icon: const Icon(Icons.pause),
                    label: const Text('Pause'),
                    style: ElevatedButton.styleFrom(backgroundColor: Colors.amber.shade700, foregroundColor: Colors.white),
                  )
                else
                  ElevatedButton.icon(
                    onPressed: _sessionActionLoading ? null : () => _updateSessionAction('resume'),
                    icon: const Icon(Icons.play_arrow),
                    label: const Text('Resume'),
                    style: ElevatedButton.styleFrom(backgroundColor: Colors.green, foregroundColor: Colors.white),
                  ),
                ElevatedButton.icon(
                  onPressed: _sessionActionLoading ? null : () => _updateSessionAction('complete'),
                  icon: const Icon(Icons.check_circle_outline),
                  label: const Text('Complete'),
                  style: ElevatedButton.styleFrom(backgroundColor: Colors.blue.shade700, foregroundColor: Colors.white),
                ),
                OutlinedButton.icon(
                  onPressed: _sessionActionLoading ? null : () => _updateSessionAction('abandon'),
                  icon: const Icon(Icons.close),
                  label: const Text('End Early'),
                  style: OutlinedButton.styleFrom(foregroundColor: Colors.red.shade700),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildDashboard() {
    if (_loading) return const Center(child: CircularProgressIndicator());
    if (_summary == null) return const Center(child: Text('Failed to load data'));
    if (_summary!.containsKey('error')) {
      final errorMsg = _summary!['error'].toString();
      final isAuthError = errorMsg.contains('401') || errorMsg.contains('403') || errorMsg.toLowerCase().contains('session expired');
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(
                isAuthError ? Icons.lock_clock_outlined : Icons.cloud_off_outlined,
                size: 56,
                color: isAuthError ? Colors.amber[700] : Colors.red[400],
              ),
              const SizedBox(height: 16),
              Text(
                isAuthError ? 'Session Expired' : 'Unable to Load Dashboard',
                style: const TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 8),
              Text(
                isAuthError
                    ? 'Your authentication session has expired. Please log in again to continue.'
                    : errorMsg,
                textAlign: TextAlign.center,
                style: const TextStyle(color: Colors.grey),
              ),
              const SizedBox(height: 20),
              if (isAuthError)
                ElevatedButton.icon(
                  onPressed: () => context.read<AuthManager>().logout(),
                  icon: const Icon(Icons.login),
                  label: const Text('Log In Again'),
                )
              else
                ElevatedButton.icon(
                  onPressed: () {
                    setState(() => _loading = true);
                    _loadSummary();
                    _loadActiveSession();
                    _loadRecommendation(demoScenario: _currentDemoScenario);
                  },
                  icon: const Icon(Icons.refresh),
                  label: const Text('Retry'),
                ),
            ],
          ),
        ),
      );
    }

    final totalActive = _summary!['total_active_seconds'] ?? 0;
    final breakdown = _summary!['category_breakdown'] ?? {};
    final productive = (breakdown['productive_work'] ?? 0) +
        (breakdown['development'] ?? 0) +
        (breakdown['productivity'] ?? 0) +
        (breakdown['office'] ?? 0) +
        (breakdown['learning_research'] ?? 0);
    final distracting = (breakdown['potential_distraction'] ?? 0) +
        (breakdown['entertainment'] ?? 0) +
        (breakdown['video_streaming'] ?? 0) +
        (breakdown['social'] ?? 0) +
        (breakdown['gaming'] ?? 0);

    return RefreshIndicator(
      onRefresh: () async {
        await _loadSummary();
        await _loadActiveSession();
        await _loadRecommendation(demoScenario: _currentDemoScenario);
      },
      child: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Today\'s Summary', style: Theme.of(context).textTheme.headlineSmall),
              IconButton(
                tooltip: 'Refresh Summary',
                icon: const Icon(Icons.refresh, size: 20),
                onPressed: () {
                  _loadSummary();
                  _loadActiveSession();
                  _loadRecommendation(demoScenario: _currentDemoScenario);
                },
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(child: StatCard(title: 'Active Time', value: _formatDuration(totalActive))),
              const SizedBox(width: 16),
              Expanded(child: StatCard(title: 'Productive', value: _formatDuration(productive), color: Colors.green)),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Expanded(child: StatCard(title: 'Distracting', value: _formatDuration(distracting), color: Colors.orange)),
              const SizedBox(width: 16),
              Expanded(child: StatCard(title: 'Focus Sessions', value: '${_summary!['focus_sessions_count'] ?? 0}')),
            ],
          ),
          const SizedBox(height: 24),
          _buildRecommendationCard(),
          const SizedBox(height: 16),
          _buildFocusSessionCard(),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final screens = [_buildDashboard(), const TimelineScreen(), const SettingsScreen()];
    final userEmail = context.watch<AuthManager>().userEmail;

    return Scaffold(
      appBar: AppBar(
        title: const Text('FlowState'),
        actions: [
          if (userEmail != null)
            Center(
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8.0),
                child: Chip(
                  avatar: const Icon(Icons.person_outline, size: 16),
                  label: Text(userEmail, style: const TextStyle(fontSize: 12)),
                  visualDensity: VisualDensity.compact,
                ),
              ),
            ),
          IconButton(
            tooltip: 'Logout',
            icon: const Icon(Icons.logout),
            onPressed: () => context.read<AuthManager>().logout(),
          )
        ],
      ),
      body: screens[_currentIndex],
      bottomNavigationBar: BottomNavigationBar(
        currentIndex: _currentIndex,
        onTap: (i) {
          setState(() => _currentIndex = i);
          if (i == 0) {
            _loadSummary();
            _loadActiveSession();
            _loadRecommendation(demoScenario: _currentDemoScenario);
          }
        },
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.dashboard), label: 'Dashboard'),
          BottomNavigationBarItem(icon: Icon(Icons.timeline), label: 'Timeline'),
          BottomNavigationBarItem(icon: Icon(Icons.settings), label: 'Settings'),
        ],
      ),
    );
  }
}
