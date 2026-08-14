import 'package:dio/dio.dart';

String extractApiError(DioException exception, {required String fallback}) {
  final data = exception.response?.data;
  if (data is Map<String, dynamic>) {
    final erro = data['erro'];
    if (erro is String && erro.trim().isNotEmpty) {
      return erro.trim();
    }
  }
  return fallback;
}

String extractErrorMessage(Object error, {required String fallback}) {
  if (error is DioException) {
    return extractApiError(error, fallback: fallback);
  }

  final message = error.toString().replaceFirst('Exception: ', '').trim();
  if (message.isEmpty) {
    return fallback;
  }
  return message;
}
