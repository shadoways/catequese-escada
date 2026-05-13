import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:app_flutter/core/session/auth_session_controller.dart';
import 'package:app_flutter/features/admin/presentation/admin_home_page.dart';
import 'package:app_flutter/features/auth/presentation/loading_page.dart';
import 'package:app_flutter/features/auth/presentation/login_page.dart';
import 'package:app_flutter/features/catequisandos/presentation/catequisando_detail_page.dart';
import 'package:app_flutter/features/catequisandos/presentation/catequisando_list_page.dart';
import 'package:app_flutter/features/operational/presentation/operational_home_page.dart';
import 'package:app_flutter/features/public_registration/presentation/public_registration_page.dart';

class AppRouter {
  AppRouter(this._session) {
    router = GoRouter(
      debugLogDiagnostics: kDebugMode,
      initialLocation: '/loading',
      refreshListenable: _session,
      redirect: _redirect,
      routes: [
        GoRoute(
          path: '/loading',
          builder: (context, state) => const LoadingPage(),
        ),
        GoRoute(path: '/login', builder: (context, state) => const LoginPage()),
        GoRoute(
          path: '/cadastro-publico',
          builder: (context, state) {
            final token = state.uri.queryParameters['token']?.trim() ?? '';
            return PublicRegistrationPage(inviteToken: token);
          },
        ),
        GoRoute(
          path: '/admin',
          builder: (context, state) => const AdminHomePage(),
        ),
        GoRoute(
          path: '/operacional',
          builder: (context, state) => const OperationalHomePage(),
        ),
        GoRoute(
          path: '/operacional/catequisandos',
          builder: (context, state) => const CatequisandoListPage(),
        ),
        GoRoute(
          path: '/operacional/catequisandos/:id',
          builder: (context, state) {
            final id = int.tryParse(state.pathParameters['id'] ?? '') ?? 0;
            return CatequisandoDetailPage(idCatequisando: id);
          },
        ),
      ],
      errorBuilder: (context, state) => Scaffold(
        body: Center(child: Text('Rota não encontrada: ${state.uri.path}')),
      ),
    );
  }

  final AuthSessionController _session;
  late final GoRouter router;

  void dispose() {
    router.dispose();
  }

  String? _redirect(BuildContext context, GoRouterState state) {
    final location = state.uri.path;
    final isLoading = location == '/loading';
    final isLogin = location == '/login';
    final isPublicRegistration = location == '/cadastro-publico';

    if (!_session.initialized) {
      return isLoading ? null : '/loading';
    }

    if (isLoading) {
      return _session.isAuthenticated
          ? (_session.isAdmin ? '/admin' : '/operacional')
          : '/login';
    }

    if (isPublicRegistration) {
      final token = state.uri.queryParameters['token']?.trim() ?? '';
      if (token.isEmpty) {
        return '/login';
      }
      return null;
    }

    if (!_session.isAuthenticated) {
      return isLogin ? null : '/login';
    }

    if (isLogin || location == '/') {
      return _session.isAdmin ? '/admin' : '/operacional';
    }

    if (location.startsWith('/admin') && !_session.isAdmin) {
      return '/operacional';
    }

    if (location.startsWith('/operacional') && _session.isAdmin) {
      return '/admin';
    }

    return null;
  }
}
