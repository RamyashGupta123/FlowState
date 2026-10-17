import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'api_client.dart';

class AuthManager with ChangeNotifier {
  bool _isAuthenticated = false;
  bool _isLoading = true;
  String? _userEmail;

  bool get isAuthenticated => _isAuthenticated;
  bool get isLoading => _isLoading;
  String? get userEmail => _userEmail;

  AuthManager() {
    ApiClient.onUnauthorized = () {
      logout();
    };
    _checkAuth();
  }

  static String? extractEmail(String token) {
    try {
      final parts = token.split('.');
      if (parts.length != 3) return null;
      final normalized = base64Url.normalize(parts[1]);
      final payload = jsonDecode(utf8.decode(base64Url.decode(normalized)));
      return payload['email']?.toString();
    } catch (_) {
      return null;
    }
  }

  Future<void> _checkAuth() async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString('jwt_token');
    if (token != null && !ApiClient.isTokenExpired(token)) {
      _isAuthenticated = true;
      _userEmail = extractEmail(token);
    } else {
      if (token != null) {
        await prefs.remove('jwt_token');
      }
      _isAuthenticated = false;
      _userEmail = null;
    }
    _isLoading = false;
    notifyListeners();
  }

  Future<bool> login(String email, String password) async {
    try {
      final res = await ApiClient.post('/auth/login', {'email': email, 'password': password});
      if (res.statusCode == 200) {
        final data = jsonDecode(res.body);
        final token = data['token'] as String;
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString('jwt_token', token);
        _isAuthenticated = true;
        _userEmail = extractEmail(token) ?? email;
        notifyListeners();
        return true;
      }
    } catch (e) {
      debugPrint(e.toString());
    }
    return false;
  }
  
  Future<bool> register(String email, String password) async {
    try {
      final res = await ApiClient.post('/auth/register', {'email': email, 'password': password});
      if (res.statusCode == 201) {
        final data = jsonDecode(res.body);
        final token = data['token'] as String;
        final prefs = await SharedPreferences.getInstance();
        await prefs.setString('jwt_token', token);
        _isAuthenticated = true;
        _userEmail = extractEmail(token) ?? email;
        notifyListeners();
        return true;
      }
    } catch (e) {
      debugPrint(e.toString());
    }
    return false;
  }

  Future<void> logout() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove('jwt_token');
    _isAuthenticated = false;
    _userEmail = null;
    notifyListeners();
  }
}
