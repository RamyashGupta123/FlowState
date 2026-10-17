import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../core/auth_manager.dart';
import '../core/api_client.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _isLogin = true;
  bool _loading = false;

  Future<void> _submit() async {
    setState(() => _loading = true);
    final auth = context.read<AuthManager>();
    final success = _isLogin 
      ? await auth.login(_emailController.text, _passwordController.text)
      : await auth.register(_emailController.text, _passwordController.text);
    
    if (!mounted) return;
    setState(() => _loading = false);
    if (!success) {
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Authentication failed')));
    }
  }

  Future<void> _editServerUrl() async {
    final current = await ApiClient.getBaseUrl();
    final controller = TextEditingController(text: current);
    if (!mounted) return;
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('API Server URL'),
        content: TextField(
          controller: controller,
          decoration: const InputDecoration(labelText: 'Base URL', hintText: 'http://localhost:8080/api'),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Cancel')),
          ElevatedButton(
            onPressed: () async {
              if (controller.text.trim().isNotEmpty) {
                await ApiClient.setBaseUrl(controller.text.trim());
              }
              if (ctx.mounted) Navigator.pop(ctx);
              if (mounted) setState(() {});
            },
            child: const Text('Save'),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(_isLogin ? 'Login to FlowState' : 'Register', style: Theme.of(context).textTheme.headlineMedium),
              const SizedBox(height: 24),
              TextField(controller: _emailController, decoration: const InputDecoration(labelText: 'Email')),
              const SizedBox(height: 16),
              TextField(controller: _passwordController, decoration: const InputDecoration(labelText: 'Password'), obscureText: true),
              const SizedBox(height: 24),
              _loading 
                ? const CircularProgressIndicator() 
                : ElevatedButton(onPressed: _submit, child: Text(_isLogin ? 'Login' : 'Register')),
              TextButton(
                onPressed: () => setState(() => _isLogin = !_isLogin),
                child: Text(_isLogin ? 'Need an account? Register' : 'Have an account? Login'),
              ),
              const SizedBox(height: 12),
              TextButton.icon(
                onPressed: _editServerUrl,
                icon: const Icon(Icons.dns, size: 14, color: Colors.grey),
                label: FutureBuilder<String>(
                  future: ApiClient.getBaseUrl(),
                  builder: (c, snap) => Text(
                    'Server: ${snap.data ?? "http://localhost:8080/api"}',
                    style: const TextStyle(fontSize: 11, color: Colors.grey),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
