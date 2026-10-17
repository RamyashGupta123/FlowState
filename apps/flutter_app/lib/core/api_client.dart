import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class ApiClient {
  static const String defaultBaseUrl = 'http://localhost:8080/api';
  static VoidCallback? onUnauthorized;

  static bool isTokenExpired(String token) {
    try {
      final parts = token.split('.');
      if (parts.length != 3) return true;
      final normalized = base64Url.normalize(parts[1]);
      final payload = jsonDecode(utf8.decode(base64Url.decode(normalized)));
      if (payload['exp'] == null) return false;
      final exp = payload['exp'] as int;
      final expiry = DateTime.fromMillisecondsSinceEpoch(exp * 1000);
      return DateTime.now().isAfter(expiry);
    } catch (_) {
      return true;
    }
  }

  static Future<http.Response> _handleResponse(http.Response res, String endpoint) async {
    // Avoid triggering logout loops on authentication endpoint failures
    if (!endpoint.startsWith('/auth/')) {
      if (res.statusCode == 401 || res.statusCode == 403) {
        final prefs = await SharedPreferences.getInstance();
        final token = prefs.getString('jwt_token');
        if (res.statusCode == 401 || token == null || isTokenExpired(token)) {
          await prefs.remove('jwt_token');
          onUnauthorized?.call();
        }
      }
    }
    return res;
  }

  static Future<String> getBaseUrl() async {
    final prefs = await SharedPreferences.getInstance();
    final customUrl = prefs.getString('api_base_url');
    if (customUrl != null && customUrl.trim().isNotEmpty) {
      return customUrl.trim().replaceAll(RegExp(r'/+$'), '');
    }
    
    // Dynamic host derivation for remote web dashboard participants
    try {
      final host = Uri.base.host;
      if (host.isNotEmpty && host != 'localhost' && host != '127.0.0.1') {
        final scheme = Uri.base.scheme.isNotEmpty ? Uri.base.scheme : 'http';
        return '$scheme://$host:8080/api';
      }
    } catch (_) {}

    return defaultBaseUrl;
  }

  static Future<void> setBaseUrl(String url) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('api_base_url', url.trim());
  }

  static Future<Map<String, String>> _getHeaders() async {
    final prefs = await SharedPreferences.getInstance();
    final token = prefs.getString('jwt_token');
    return {
      'Content-Type': 'application/json',
      if (token != null) 'Authorization': 'Bearer $token',
    };
  }

  static Future<http.Response> post(String endpoint, Map<String, dynamic> body) async {
    final baseUrl = await getBaseUrl();
    final res = await http.post(
      Uri.parse('$baseUrl$endpoint'),
      headers: await _getHeaders(),
      body: jsonEncode(body),
    );
    return _handleResponse(res, endpoint);
  }

  static Future<http.Response> put(String endpoint, Map<String, dynamic> body) async {
    final baseUrl = await getBaseUrl();
    final res = await http.put(
      Uri.parse('$baseUrl$endpoint'),
      headers: await _getHeaders(),
      body: jsonEncode(body),
    );
    return _handleResponse(res, endpoint);
  }

  static Future<http.Response> get(String endpoint) async {
    final baseUrl = await getBaseUrl();
    final res = await http.get(
      Uri.parse('$baseUrl$endpoint'),
      headers: await _getHeaders(),
    );
    return _handleResponse(res, endpoint);
  }

  static Future<http.Response> delete(String endpoint) async {
    final baseUrl = await getBaseUrl();
    final res = await http.delete(
      Uri.parse('$baseUrl$endpoint'),
      headers: await _getHeaders(),
    );
    return _handleResponse(res, endpoint);
  }
}
