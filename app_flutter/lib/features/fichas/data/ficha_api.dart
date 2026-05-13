import 'package:app_flutter/core/network/api_client.dart';

class FichaInscricao {
  const FichaInscricao({
    required this.idFicha,
    required this.dataInscricao,
    required this.observacoes,
  });

  final int idFicha;
  final String dataInscricao;
  final String observacoes;

  factory FichaInscricao.fromJson(Map<String, dynamic> json) {
    int toInt(dynamic value) {
      if (value is int) {
        return value;
      }
      return int.tryParse((value ?? '').toString()) ?? 0;
    }

    return FichaInscricao(
      idFicha: toInt(json['idFicha']),
      dataInscricao: (json['dataInscricao'] ?? '').toString(),
      observacoes: (json['observacoes'] ?? '').toString(),
    );
  }
}

class FichaInscricaoRequest {
  const FichaInscricaoRequest({
    required this.dataInscricao,
    required this.observacoes,
  });

  final String dataInscricao;
  final String observacoes;

  Map<String, dynamic> toJson() {
    return {'dataInscricao': dataInscricao, 'observacoes': observacoes};
  }
}

class FichaApi {
  FichaApi(this._client);

  final ApiClient _client;

  Future<List<FichaInscricao>> fetchByCatequisando(int idCatequisando) async {
    final response = await _client.raw.get<List<dynamic>>(
      '/api/catequisandos/$idCatequisando/fichas/',
    );

    final list = response.data ?? const <dynamic>[];
    return list
        .whereType<Map<String, dynamic>>()
        .map(FichaInscricao.fromJson)
        .toList();
  }

  Future<FichaInscricao> create(
    int idCatequisando,
    FichaInscricaoRequest request,
  ) async {
    final response = await _client.raw.post<Map<String, dynamic>>(
      '/api/catequisandos/$idCatequisando/fichas/',
      data: request.toJson(),
    );

    return FichaInscricao.fromJson(response.data ?? const <String, dynamic>{});
  }
}
