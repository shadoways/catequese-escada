import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';

class FakeDioAdapter implements HttpClientAdapter {
  final Map<String, _FakeRoute> _routes = <String, _FakeRoute>{};
  final List<RequestOptions> requests = <RequestOptions>[];

  void on(
    String method,
    String path, {
    int statusCode = 200,
    Object? data,
    Map<String, List<String>>? headers,
  }) {
    final key = _key(method, path);
    _routes[key] = _FakeRoute(
      statusCode: statusCode,
      data: data,
      headers: headers ??
          <String, List<String>>{
            Headers.contentTypeHeader: <String>[Headers.jsonContentType],
          },
    );
  }

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    requests.add(options);

    final key = _key(options.method, options.path);
    final route = _routes[key];
    if (route == null) {
      return ResponseBody.fromString(
        '{"error":"not found"}',
        404,
        headers: <String, List<String>>{
          Headers.contentTypeHeader: <String>[Headers.jsonContentType],
        },
      );
    }

    final body = route.data == null ? '' : jsonEncode(route.data);
    return ResponseBody.fromString(
      body,
      route.statusCode,
      headers: route.headers,
    );
  }

  @override
  void close({bool force = false}) {}

  String _key(String method, String path) {
    return '${method.toUpperCase()} $path';
  }
}

class _FakeRoute {
  const _FakeRoute({
    required this.statusCode,
    required this.data,
    required this.headers,
  });

  final int statusCode;
  final Object? data;
  final Map<String, List<String>> headers;
}
