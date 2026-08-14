import 'package:app_flutter/core/network/api_client.dart';
import 'package:app_flutter/features/catequisandos/data/catequisando_api.dart';
import 'package:flutter_test/flutter_test.dart';

import '../../../support/fake_dio_adapter.dart';

void main() {
  late ApiClient client;
  late FakeDioAdapter adapter;
  late CatequisandoApi api;

  setUp(() {
    client = ApiClient(
      baseUrl: 'http://localhost:8080',
      getAccessToken: () => null,
      onUnauthorized: () async => false,
    );
    adapter = FakeDioAdapter();
    client.raw.httpClientAdapter = adapter;
    api = CatequisandoApi(client);
  });

  tearDown(() {
    client.dispose();
  });

  test('fetchAll mapeia lista de catequisandos', () async {
    adapter.on(
      'GET',
      '/api/catequisandos/',
      data: <Map<String, Object?>>[
        <String, Object?>{
          'idCatequisando': '11',
          'nome': 'Ana',
          'ativo': true,
          'arquivos': <String, Object?>{'total': '3'},
        },
      ],
    );

    final result = await api.fetchAll();

    expect(result, hasLength(1));
    expect(result.first.id, 11);
    expect(result.first.nome, 'Ana');
    expect(result.first.ativo, true);
    expect(result.first.documentosTotal, 3);
    expect(adapter.requests.single.path, '/api/catequisandos/');
  });

  test('fetchById mapeia detalhe completo', () async {
    adapter.on(
      'GET',
      '/api/catequisandos/7',
      data: <String, Object?>{
        'idCatequisando': 7,
        'nome': 'Joao',
        'ativo': true,
        'email': 'joao@email.com',
        'telefone': '119999999',
        'dataNascimento': '2010-01-02',
        'nomeResponsavel': 'Maria',
        'telefoneResponsavel': '118888888',
        'endereco': 'Rua A',
        'turma': <String, Object?>{'nome': 'Turma 1'},
        'comunidade': <String, Object?>{'nome': 'Comunidade 1'},
        'arquivos': <String, Object?>{'total': 2},
      },
    );

    final detail = await api.fetchById(7);

    expect(detail.id, 7);
    expect(detail.nome, 'Joao');
    expect(detail.turmaNome, 'Turma 1');
    expect(detail.comunidadeNome, 'Comunidade 1');
    expect(detail.documentosTotal, 2);
    expect(adapter.requests.single.path, '/api/catequisandos/7');
  });
}
