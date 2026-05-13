import 'package:app_flutter/core/network/api_client.dart';

class CatequisandoSummary {
  const CatequisandoSummary({
    required this.id,
    required this.nome,
    required this.ativo,
    required this.documentosTotal,
  });

  final int id;
  final String nome;
  final bool ativo;
  final int documentosTotal;

  factory CatequisandoSummary.fromJson(Map<String, dynamic> json) {
    final rawId = json['idCatequisando'];
    final arquivos = json['arquivos'];

    return CatequisandoSummary(
      id: rawId is int ? rawId : int.tryParse(rawId.toString()) ?? 0,
      nome: (json['nome'] ?? '').toString(),
      ativo: json['ativo'] == true,
      documentosTotal: arquivos is Map<String, dynamic>
          ? ((arquivos['total'] is int)
                ? arquivos['total'] as int
                : int.tryParse((arquivos['total'] ?? '0').toString()) ?? 0)
          : 0,
    );
  }
}

class CatequisandoDetail {
  const CatequisandoDetail({
    required this.id,
    required this.nome,
    required this.ativo,
    required this.email,
    required this.telefone,
    required this.dataNascimento,
    required this.nomeResponsavel,
    required this.telefoneResponsavel,
    required this.endereco,
    required this.turmaNome,
    required this.comunidadeNome,
    required this.documentosTotal,
  });

  final int id;
  final String nome;
  final bool ativo;
  final String email;
  final String telefone;
  final String dataNascimento;
  final String nomeResponsavel;
  final String telefoneResponsavel;
  final String endereco;
  final String turmaNome;
  final String comunidadeNome;
  final int documentosTotal;

  factory CatequisandoDetail.fromJson(Map<String, dynamic> json) {
    int toInt(dynamic value) {
      if (value is int) {
        return value;
      }
      return int.tryParse((value ?? '').toString()) ?? 0;
    }

    final arquivos = json['arquivos'];
    final turma = json['turma'];
    final comunidade = json['comunidade'];

    return CatequisandoDetail(
      id: toInt(json['idCatequisando']),
      nome: (json['nome'] ?? '').toString(),
      ativo: json['ativo'] == true,
      email: (json['email'] ?? '').toString(),
      telefone: (json['telefone'] ?? '').toString(),
      dataNascimento: (json['dataNascimento'] ?? '').toString(),
      nomeResponsavel: (json['nomeResponsavel'] ?? '').toString(),
      telefoneResponsavel: (json['telefoneResponsavel'] ?? '').toString(),
      endereco: (json['endereco'] ?? '').toString(),
      turmaNome: turma is Map<String, dynamic>
          ? (turma['nome'] ?? '').toString()
          : '',
      comunidadeNome: comunidade is Map<String, dynamic>
          ? (comunidade['nome'] ?? '').toString()
          : '',
      documentosTotal: arquivos is Map<String, dynamic>
          ? toInt(arquivos['total'])
          : 0,
    );
  }
}

class CatequisandoApi {
  CatequisandoApi(this._client);

  final ApiClient _client;

  Future<List<CatequisandoSummary>> fetchAll() async {
    final response = await _client.raw.get<List<dynamic>>(
      '/api/catequisandos/',
    );
    final list = response.data ?? const <dynamic>[];
    return list
        .whereType<Map<String, dynamic>>()
        .map(CatequisandoSummary.fromJson)
        .toList();
  }

  Future<CatequisandoDetail> fetchById(int idCatequisando) async {
    final response = await _client.raw.get<Map<String, dynamic>>(
      '/api/catequisandos/$idCatequisando',
    );
    return CatequisandoDetail.fromJson(
      response.data ?? const <String, dynamic>{},
    );
  }
}
