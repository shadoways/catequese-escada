import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:app_flutter/core/network/api_error.dart';

void main() {
  DioException dioErrorWithData(Object? data) {
    return DioException(
      requestOptions: RequestOptions(path: '/api/test'),
      response: Response<dynamic>(
        requestOptions: RequestOptions(path: '/api/test'),
        data: data,
        statusCode: 400,
      ),
      type: DioExceptionType.badResponse,
    );
  }

  test('extractApiError retorna campo erro quando presente', () {
    final exception = dioErrorWithData(<String, dynamic>{
      'erro': 'Data de inscrição inválida',
    });

    final message = extractApiError(
      exception,
      fallback: 'Falha genérica',
    );

    expect(message, 'Data de inscrição inválida');
  });

  test('extractApiError usa fallback quando payload não tem erro', () {
    final exception = dioErrorWithData(<String, dynamic>{'message': 'x'});

    final message = extractApiError(
      exception,
      fallback: 'Falha genérica',
    );

    expect(message, 'Falha genérica');
  });

  test('extractErrorMessage usa extractApiError para DioException', () {
    final exception = dioErrorWithData(<String, dynamic>{
      'erro': 'Token inválido',
    });

    final message = extractErrorMessage(
      exception,
      fallback: 'Falha genérica',
    );

    expect(message, 'Token inválido');
  });

  test('extractErrorMessage trata Exception comum', () {
    final message = extractErrorMessage(
      Exception('Erro customizado'),
      fallback: 'Falha genérica',
    );

    expect(message, 'Erro customizado');
  });
}
