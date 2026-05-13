import 'package:dio/dio.dart';

import 'package:app_flutter/core/network/api_client.dart';
import 'package:app_flutter/core/session/auth_session_controller.dart';

class AuthApi {
  AuthApi(this._client);

  final ApiClient _client;

  Future<UserSession> login({
    required String email,
    required String password,
  }) async {
    try {
      final response = await _client.raw.post<Map<String, dynamic>>(
        '/api/auth/login',
        data: {'email': email, 'password': password},
        options: Options(extra: {'skipAuthRefresh': true}),
      );
      return _mapSession(response.data ?? const <String, dynamic>{});
    } on DioException catch (e) {
      throw Exception(_extractApiError(e, fallback: 'Falha no login'));
    }
  }

  Future<UserSession> refresh(String refreshToken) async {
    try {
      final response = await _client.raw.post<Map<String, dynamic>>(
        '/api/auth/refresh',
        data: {'refreshToken': refreshToken},
        options: Options(extra: {'skipAuthRefresh': true}),
      );
      return _mapSession(response.data ?? const <String, dynamic>{});
    } on DioException catch (e) {
      throw Exception(_extractApiError(e, fallback: 'Falha ao renovar sessão'));
    }
  }

  UserSession _mapSession(Map<String, dynamic> raw) {
    final token = (raw['token'] ?? '').toString();
    final refreshToken = (raw['refreshToken'] ?? '').toString();
    final email = (raw['email'] ?? '').toString();

    final roleValues = (raw['roles'] as List<dynamic>? ?? const <dynamic>[])
        .map((e) => e.toString())
        .toList();

    final roles = roleValues.map(AppRole.fromApi).whereType<AppRole>().toSet();

    if (token.isEmpty || refreshToken.isEmpty || email.isEmpty) {
      throw Exception('Resposta de autenticação inválida');
    }

    return UserSession(
      accessToken: token,
      refreshToken: refreshToken,
      email: email,
      roles: roles,
    );
  }

  String _extractApiError(DioException exception, {required String fallback}) {
    final data = exception.response?.data;
    if (data is Map<String, dynamic>) {
      final erro = data['erro'];
      if (erro is String && erro.trim().isNotEmpty) {
        return erro.trim();
      }
    }
    return fallback;
  }
}
