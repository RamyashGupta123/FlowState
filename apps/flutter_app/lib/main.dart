import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'core/auth_manager.dart';
import 'screens/login_screen.dart';
import 'screens/dashboard_screen.dart';
import 'theme.dart';

void main() {
  runApp(
    MultiProvider(
      providers: [
        ChangeNotifierProvider(create: (_) => AuthManager()),
      ],
      child: const FlowStateApp(),
    ),
  );
}

class FlowStateApp extends StatelessWidget {
  const FlowStateApp({super.key});
  
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'FlowState',
      theme: AppTheme.lightTheme,
      home: Consumer<AuthManager>(
        builder: (context, auth, _) {
          if (auth.isLoading) return const Scaffold(body: Center(child: CircularProgressIndicator()));
          return auth.isAuthenticated ? const DashboardScreen() : const LoginScreen();
        },
      ),
    );
  }
}
