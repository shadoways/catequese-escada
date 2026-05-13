import 'package:dio/dio.dart';

import 'package:app_flutter/core/security/correlation_id.dart';

typedef AccessTokenReader = String? Function();
typedef UnauthorizedHandler = Future<bool> Function();

class ApiClient {
  ApiClient({
    required String baseUrl,
    required AccessTokenReader getAccessToken,
    required UnauthorizedHandler onUnauthorized,
  }) : _dio = Dio(
         BaseOptions(
           baseUrl: baseUrl,
           connectTimeout: const Duration(seconds: 15),
           receiveTimeout: const Duration(seconds: 20),
           sendTimeout: const Duration(seconds: 20),
         ),
       ) {
    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) {
          final token = getAccessToken();
          if (token != null && token.isNotEmpty) {
            options.headers['Authorization'] = 'Bearer $token';
          }
          options.headers['X-Correlation-ID'] = CorrelationId.generate();
          handler.next(options);
        },
        onError: (error, handler) {
          final requestOptions = error.requestOptions;
          final skipRefresh = requestOptions.extra['skipAuthRefresh'] == true;
          final alreadyRetried =
              requestOptions.extra['retriedAfterRefresh'] == true;

          if (!skipRefresh &&
              error.response?.statusCode == 401 &&
              !alreadyRetried) {
            onUnauthorized().then((refreshed) async {
              if (!refreshed) {
                handler.next(error);
                return;
              }

              final retryOptions = Options(
                method: requestOptions.method,
                headers: Map<String, dynamic>.from(requestOptions.headers),
                responseType: requestOptions.responseType,
                contentType: requestOptions.contentType,
                receiveDataWhenStatusError:
                    requestOptions.receiveDataWhenStatusError,
                extra: {...requestOptions.extra, 'retriedAfterRefresh': true},
              );

              final token = getAccessToken();
              if (token != null && token.isNotEmpty) {
                retryOptions.headers ??= <String, dynamic>{};
                retryOptions.headers!['Authorization'] = 'Bearer $token';
              }

              try {
                final response = await _dio.request<dynamic>(
                  requestOptions.path,
                  data: requestOptions.data,
                  queryParameters: requestOptions.queryParameters,
                  options: retryOptions,
                  cancelToken: requestOptions.cancelToken,
                  onSendProgress: requestOptions.onSendProgress,
                  onReceiveProgress: requestOptions.onReceiveProgress,
                );
                handler.resolve(response);
              } catch (e) {
                if (e is DioException) {
                  handler.next(e);
                  return;
                }
                handler.next(error);
              }
            });
            return;
          }

          handler.next(error);
        },
      ),
    );
  }

  final Dio _dio;

  Dio get raw => _dio;

  void dispose() {
    _dio.close();
  }
}
