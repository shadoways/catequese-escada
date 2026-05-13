import 'package:flutter/foundation.dart';

import 'package:app_flutter/core/auth/auth_api.dart';
import 'package:app_flutter/core/session/session_store.dart';

enum AppRole {
  coordenadorParoquial('COORDENADOR_PAROQUIAL'),
  coordenadorComunidade('COORDENADOR_COMUNIDADE'),
  catequista('CATEQUISTA');

  const AppRole(this.value);
  final String value;

  static AppRole? fromApi(String value) {
    for (final role in AppRole.values) {
      if (role.value == value) {
        return role;
      }
    }
    return null;
  }
}

class UserSession {
  const UserSession({
    required this.accessToken,
    required this.refreshToken,
    required this.email,
    required this.roles,
  });

  final String accessToken;
  final String refreshToken;
  final String email;
  final Set<AppRole> roles;

  bool hasRole(AppRole role) => roles.contains(role);

  Map<String, dynamic> toJson() {
    return {
      'accessToken': accessToken,
      'refreshToken': refreshToken,
      'email': email,
      'roles': roles.map((r) => r.value).toList(),
    };
  }

  factory UserSession.fromJson(Map<String, dynamic> json) {
    final roleValues = (json['roles'] as List<dynamic>? ?? const <dynamic>[])
        .map((e) => e.toString())
        .toList();

    return UserSession(
      accessToken: (json['accessToken'] ?? '').toString(),
      refreshToken: (json['refreshToken'] ?? '').toString(),
      email: (json['email'] ?? '').toString(),
      roles: roleValues.map(AppRole.fromApi).whereType<AppRole>().toSet(),
    );
  }
}

class AuthSessionController extends ChangeNotifier {
  AuthSessionController({
    required AuthApi authApi,
    required SessionStore sessionStore,
  }) : _authApi = authApi,
       _sessionStore = sessionStore;

  final AuthApi _authApi;
  final SessionStore _sessionStore;

  UserSession? _currentSession;
  bool _initialized = false;
  bool _refreshInProgress = false;

  UserSession? get currentSession => _currentSession;
  bool get isAuthenticated => _currentSession != null;
  bool get isAdmin =>
      _currentSession?.hasRole(AppRole.coordenadorParoquial) ?? false;
  bool get initialized => _initialized;

  Future<void> initialize() async {
    _currentSession = await _sessionStore.read();
    _initialized = true;
    notifyListeners();
  }

  Future<void> signIn({required String email, required String password}) async {
    final normalized = email.trim().toLowerCase();
    if (normalized.isEmpty || password.trim().isEmpty) {
      throw Exception('Email e senha são obrigatórios');
    }

    _currentSession = await _authApi.login(
      email: normalized,
      password: password,
    );
    await _sessionStore.save(_currentSession!);
    notifyListeners();
  }

  Future<bool> tryRefreshSession() async {
    if (_refreshInProgress) {
      return false;
    }
    final refreshToken = _currentSession?.refreshToken;
    if (refreshToken == null || refreshToken.isEmpty) {
      return false;
    }

    _refreshInProgress = true;
    try {
      final renewed = await _authApi.refresh(refreshToken);
      _currentSession = renewed;
      await _sessionStore.save(renewed);
      notifyListeners();
      return true;
    } catch (_) {
      await forceLogout();
      return false;
    } finally {
      _refreshInProgress = false;
    }
  }

  Future<void> forceLogout() async {
    _currentSession = null;
    await _sessionStore.clear();
    notifyListeners();
  }
}
