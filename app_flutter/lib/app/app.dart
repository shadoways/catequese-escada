import 'package:flutter/material.dart';

import 'package:app_flutter/app/router/app_router.dart';
import 'package:app_flutter/core/auth/auth_api.dart';
import 'package:app_flutter/app/theme/app_theme.dart';
import 'package:app_flutter/core/network/api_client.dart';
import 'package:app_flutter/core/session/auth_session_controller.dart';
import 'package:app_flutter/core/session/session_store.dart';

class CatequeseApp extends StatefulWidget {
  const CatequeseApp({super.key});

  @override
  State<CatequeseApp> createState() => _CatequeseAppState();
}

class _CatequeseAppState extends State<CatequeseApp> {
  late final AuthSessionController _session;
  late final AppRouter _appRouter;
  late final ApiClient _apiClient;
  late final AuthApi _authApi;
  late final SessionStore _sessionStore;

  @override
  void initState() {
    super.initState();
    _sessionStore = SessionStore();
    _apiClient = ApiClient(
      baseUrl: const String.fromEnvironment(
        'API_BASE_URL',
        defaultValue: 'http://localhost:8080',
      ),
      getAccessToken: () => _session.currentSession?.accessToken,
      onUnauthorized: () => _session.tryRefreshSession(),
    );
    _authApi = AuthApi(_apiClient);
    _session = AuthSessionController(
      authApi: _authApi,
      sessionStore: _sessionStore,
    );
    _session.initialize();

    _appRouter = AppRouter(_session);
  }

  @override
  void dispose() {
    _appRouter.dispose();
    _session.dispose();
    _apiClient.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return InheritedSessionScope(
      session: _session,
      apiClient: _apiClient,
      child: MaterialApp.router(
        title: 'Catequese CRM',
        debugShowCheckedModeBanner: false,
        theme: AppTheme.light,
        routerConfig: _appRouter.router,
      ),
    );
  }
}

class InheritedSessionScope extends InheritedWidget {
  const InheritedSessionScope({
    super.key,
    required this.session,
    required this.apiClient,
    required super.child,
  });

  final AuthSessionController session;
  final ApiClient apiClient;

  static InheritedSessionScope of(BuildContext context) {
    final scope = context
        .dependOnInheritedWidgetOfExactType<InheritedSessionScope>();
    assert(scope != null, 'InheritedSessionScope not found in widget tree');
    return scope!;
  }

  @override
  bool updateShouldNotify(covariant InheritedSessionScope oldWidget) {
    return oldWidget.session != session || oldWidget.apiClient != apiClient;
  }
}
