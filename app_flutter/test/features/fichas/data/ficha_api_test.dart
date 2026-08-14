import 'package:app_flutter/core/network/api_client.dart';
import 'package:app_flutter/features/fichas/data/ficha_api.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../../support/fake_dio_adapter.dart';

void main() {
  late ApiClient client;
  late FakeDioAdapter adapter;
  late FichaApi api;

  setUp(() {
    client = ApiClient(
      baseUrl: 'http://localhost:8080',
      getAccessToken: () => null,
      onUnauthorized: () async => false,
    );
    adapter = FakeDioAdapter();
    client.raw.httpClientAdapter = adapter;
    api = FichaApi(client);
  });

  tearDown(() {
    client.dispose();
  });

  test('fetchByCatequisando lista fichas', () async {
    adapter.on(
      'GET',
      '/api/catequisandos/5/fichas/',
      data: <Map<String, Object?>>[
        <String, Object?>{
          'idFicha': '9',
          'dataInscricao': '2026-05-13',
          'observacoes': 'Primeira ficha',
        },
      ],
    );

    final fichas = await api.fetchByCatequisando(5);

    expect(fichas, hasLength(1));
    expect(fichas.first.idFicha, 9);
    expect(fichas.first.dataInscricao, '2026-05-13');
    expect(adapter.requests.single.path, '/api/catequisandos/5/fichas/');
  });

  test('create envia payload e mapeia resposta', () async {
    adapter.on(
      'POST',
      '/api/catequisandos/5/fichas/',
      data: <String, Object?>{
        'idFicha': 10,
        'dataInscricao': '2026-05-13',
        'observacoes': 'Nova ficha',
      },
    );

    final created = await api.create(
      5,
      const FichaInscricaoRequest(
        dataInscricao: '2026-05-13',
        observacoes: 'Nova ficha',
      ),
    );

    expect(created.idFicha, 10);
    expect(created.observacoes, 'Nova ficha');
    expect(adapter.requests.single.path, '/api/catequisandos/5/fichas/');
    expect(
      adapter.requests.single.data,
      <String, dynamic>{
        'dataInscricao': '2026-05-13',
        'observacoes': 'Nova ficha',
      },
    );
  });

  test('update envia payload e mapeia resposta', () async {
    adapter.on(
      'PUT',
      '/api/catequisandos/5/fichas/10',
      data: <String, Object?>{
        'idFicha': 10,
        'dataInscricao': '2026-05-20',
        'observacoes': 'Atualizada',
      },
    );

    final updated = await api.update(
      5,
      10,
      const FichaInscricaoRequest(
        dataInscricao: '2026-05-20',
        observacoes: 'Atualizada',
      ),
    );

    expect(updated.idFicha, 10);
    expect(updated.dataInscricao, '2026-05-20');
    expect(adapter.requests.single.path, '/api/catequisandos/5/fichas/10');
    expect(
      adapter.requests.single.data,
      <String, dynamic>{
        'dataInscricao': '2026-05-20',
        'observacoes': 'Atualizada',
      },
    );
  });

  test('delete chama endpoint correto', () async {
    adapter.on(
      'DELETE',
      '/api/catequisandos/5/fichas/10',
      statusCode: 204,
      headers: <String, List<String>>{},
    );

    await api.delete(5, 10);

    expect(adapter.requests.single.path, '/api/catequisandos/5/fichas/10');
  });
}
