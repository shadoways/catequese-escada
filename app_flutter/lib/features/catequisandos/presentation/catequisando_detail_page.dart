import 'package:flutter/material.dart';

import 'package:app_flutter/app/app.dart';
import 'package:app_flutter/features/catequisandos/data/catequisando_api.dart';
import 'package:app_flutter/features/fichas/data/ficha_api.dart';

class CatequisandoDetailPage extends StatefulWidget {
  const CatequisandoDetailPage({super.key, required this.idCatequisando});

  final int idCatequisando;

  @override
  State<CatequisandoDetailPage> createState() => _CatequisandoDetailPageState();
}

class _CatequisandoDetailPageState extends State<CatequisandoDetailPage> {
  final _dataController = TextEditingController();
  final _obsController = TextEditingController();

  late Future<CatequisandoDetail> _detailFuture;
  late Future<List<FichaInscricao>> _fichasFuture;
  bool _savingFicha = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _detailFuture = _loadDetail();
    _fichasFuture = _loadFichas();
  }

  @override
  void dispose() {
    _dataController.dispose();
    _obsController.dispose();
    super.dispose();
  }

  Future<CatequisandoDetail> _loadDetail() {
    final apiClient = InheritedSessionScope.of(context).apiClient;
    return CatequisandoApi(apiClient).fetchById(widget.idCatequisando);
  }

  Future<List<FichaInscricao>> _loadFichas() {
    final apiClient = InheritedSessionScope.of(context).apiClient;
    return FichaApi(apiClient).fetchByCatequisando(widget.idCatequisando);
  }

  Future<void> _createFicha() async {
    final dataInscricao = _dataController.text.trim();
    final observacoes = _obsController.text.trim();

    if (dataInscricao.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Informe a data de inscrição')),
      );
      return;
    }

    setState(() {
      _savingFicha = true;
    });

    try {
      final apiClient = InheritedSessionScope.of(context).apiClient;
      await FichaApi(apiClient).create(
        widget.idCatequisando,
        FichaInscricaoRequest(
          dataInscricao: dataInscricao,
          observacoes: observacoes,
        ),
      );

      _dataController.clear();
      _obsController.clear();

      if (!mounted) {
        return;
      }

      setState(() {
        _fichasFuture = _loadFichas();
      });

      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Ficha criada com sucesso')));
    } catch (_) {
      if (!mounted) {
        return;
      }
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Falha ao criar ficha')));
    } finally {
      if (mounted) {
        setState(() {
          _savingFicha = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Detalhe do Catequisando')),
      body: FutureBuilder<CatequisandoDetail>(
        future: _detailFuture,
        builder: (context, snapshot) {
          if (snapshot.connectionState == ConnectionState.waiting) {
            return const Center(child: CircularProgressIndicator());
          }

          if (snapshot.hasError || !snapshot.hasData) {
            return Center(
              child: TextButton(
                onPressed: () {
                  setState(() {
                    _detailFuture = _loadDetail();
                  });
                },
                child: const Text('Falha ao carregar. Tentar novamente'),
              ),
            );
          }

          final item = snapshot.data!;
          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        item.nome,
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                      const SizedBox(height: 8),
                      Text(item.ativo ? 'Status: Ativo' : 'Status: Inativo'),
                      if (item.email.isNotEmpty) Text('Email: ${item.email}'),
                      if (item.telefone.isNotEmpty)
                        Text('Telefone: ${item.telefone}'),
                      if (item.dataNascimento.isNotEmpty)
                        Text('Nascimento: ${item.dataNascimento}'),
                      if (item.nomeResponsavel.isNotEmpty)
                        Text('Responsável: ${item.nomeResponsavel}'),
                      if (item.telefoneResponsavel.isNotEmpty)
                        Text('Tel. responsável: ${item.telefoneResponsavel}'),
                      if (item.endereco.isNotEmpty)
                        Text('Endereço: ${item.endereco}'),
                      if (item.turmaNome.isNotEmpty)
                        Text('Turma: ${item.turmaNome}'),
                      if (item.comunidadeNome.isNotEmpty)
                        Text('Comunidade: ${item.comunidadeNome}'),
                      Text('Documentos: ${item.documentosTotal}'),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Nova ficha',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 10),
                      TextField(
                        controller: _dataController,
                        decoration: const InputDecoration(
                          labelText: 'Data de inscrição (YYYY-MM-DD)',
                        ),
                      ),
                      const SizedBox(height: 8),
                      TextField(
                        controller: _obsController,
                        minLines: 2,
                        maxLines: 3,
                        decoration: const InputDecoration(
                          labelText: 'Observações',
                        ),
                      ),
                      const SizedBox(height: 10),
                      Align(
                        alignment: Alignment.centerRight,
                        child: FilledButton(
                          onPressed: _savingFicha ? null : _createFicha,
                          child: Text(
                            _savingFicha ? 'Salvando...' : 'Criar ficha',
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              const SizedBox(height: 16),
              Text(
                'Histórico de fichas',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: 8),
              FutureBuilder<List<FichaInscricao>>(
                future: _fichasFuture,
                builder: (context, fichaSnapshot) {
                  if (fichaSnapshot.connectionState ==
                      ConnectionState.waiting) {
                    return const Center(child: CircularProgressIndicator());
                  }

                  if (fichaSnapshot.hasError) {
                    return TextButton(
                      onPressed: () {
                        setState(() {
                          _fichasFuture = _loadFichas();
                        });
                      },
                      child: const Text(
                        'Falha ao carregar fichas. Tentar novamente',
                      ),
                    );
                  }

                  final fichas = fichaSnapshot.data ?? const <FichaInscricao>[];
                  if (fichas.isEmpty) {
                    return const Text('Nenhuma ficha cadastrada');
                  }

                  return Column(
                    children: fichas
                        .map(
                          (ficha) => Card(
                            margin: const EdgeInsets.only(bottom: 8),
                            child: ListTile(
                              title: Text('Ficha #${ficha.idFicha}'),
                              subtitle: Text(
                                'Data: ${ficha.dataInscricao}\n${ficha.observacoes}',
                              ),
                              isThreeLine: true,
                            ),
                          ),
                        )
                        .toList(),
                  );
                },
              ),
            ],
          );
        },
      ),
    );
  }
}
