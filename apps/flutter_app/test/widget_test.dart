import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:flowstate_app/main.dart';
import 'package:flowstate_app/core/auth_manager.dart';

void main() {
  testWidgets('App starts in loading or login state', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues({});
    
    // Build our app and trigger a frame.
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider(create: (_) => AuthManager()),
        ],
        child: const FlowStateApp(),
      ),
    );

    // After first pump, AuthManager is loading, so we expect CircularProgressIndicator
    expect(find.byType(CircularProgressIndicator), findsOneWidget);

    // Let the Futures complete (loading shared prefs)
    await tester.pumpAndSettle();

    // Now it should be on the Login screen because we don't have a token
    expect(find.text('Login to FlowState'), findsOneWidget);
  });
}
