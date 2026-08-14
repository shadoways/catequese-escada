import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import 'package:app_flutter/app/app.dart';
import 'package:app_flutter/core/network/api_error.dart';
import 'package:app_flutter/features/fichas/data/ficha_api.dart';

class FichaInscricaoPage extends StatefulWidget {
  const FichaInscricaoPage({super.key, required this.idCatequisando});

  final int idCatequisando;

  @override
  State<FichaInscricaoPage> createState() => _FichaInscricaoPageState();
}

class _FichaInscricaoPageState extends State<FichaInscricaoPage> {
  static const int _fichasPageSize = 5;

  final _dataController = TextEditingController();
  final _obsController = TextEditingController();

  late Future<List<FichaInscricao>> _fichasFuture;
  bool _savingFicha = false;
  int? _busyFichaId;
  int _visibleFichasCount = _fichasPageSize;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _fichasFuture = _loadFichas();
  }

  @override
  void dispose() {
    _dataController.dispose();
    _obsController.dispose();
    super.dispose();
  }

  Future<List<FichaInscricao>> _loadFichas() {
    final apiClient = InheritedSessionScope.of(context).apiClient;
    return FichaApi(apiClient).fetchByCatequisando(widget.idCatequisando);
  }

  bool _isValidIsoDate(String value) {
    final isoDatePattern = RegExp(r'^\d{4}-\d{2}-\d{2}$');
    if (!isoDatePattern.hasMatch(value)) {
      return false;
    }

    final parsed = DateTime.tryParse(value);
    if (parsed == null) {
      return false;
    }

    final normalized =
        '${parsed.year.toString().padLeft(4, '0')}-${parsed.month.toString().padLeft(2, '0')}-${parsed.day.toString().padLeft(2, '0')}';
    return normalized == value;
  }

  void _reloadFichas() {
    setState(() {
      _visibleFichasCount = _fichasPageSize;
      _fichasFuture = _loadFichas();
    });
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

    if (!_isValidIsoDate(dataInscricao)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Data inválida. Use o formato YYYY-MM-DD')),
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

      _reloadFichas();

      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Ficha criada com sucesso')));
    } on DioException catch (e) {
      if (!mounted) {
        return;
      }

      final message = extractApiError(e, fallback: 'Falha ao criar ficha');
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(message)));
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

  Future<void> _editFicha(FichaInscricao ficha) async {
    final dataController = TextEditingController(text: ficha.dataInscricao);
    final obsController = TextEditingController(text: ficha.observacoes);

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('Editar ficha'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: dataController,
                decoration: const InputDecoration(
                  labelText: 'Data de inscrição (YYYY-MM-DD)',
                ),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: obsController,
                minLines: 2,
                maxLines: 3,
                decoration: const InputDecoration(labelText: 'Observações'),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('Cancelar'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(context).pop(true),
              child: const Text('Salvar'),
            ),
          ],
        );
      },
    );

    if (confirmed != true || !mounted) {
      dataController.dispose();
      obsController.dispose();
      return;
    }

    final dataInscricao = dataController.text.trim();
    final observacoes = obsController.text.trim();

    dataController.dispose();
    obsController.dispose();

    if (dataInscricao.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Informe a data de inscrição')),
      );
      return;
    }

    if (!_isValidIsoDate(dataInscricao)) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Data inválida. Use o formato YYYY-MM-DD')),
      );
      return;
    }

    setState(() {
      _busyFichaId = ficha.idFicha;
    });

    try {
      final apiClient = InheritedSessionScope.of(context).apiClient;
      await FichaApi(apiClient).update(
        widget.idCatequisando,
        ficha.idFicha,
        FichaInscricaoRequest(
          dataInscricao: dataInscricao,
          observacoes: observacoes,
        ),
      );

      if (!mounted) {
        return;
      }

      _reloadFichas();

      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Ficha atualizada')));
    } on DioException catch (e) {
      if (!mounted) {
        return;
      }

      final message = extractApiError(e, fallback: 'Falha ao atualizar ficha');

      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(message)));
    } catch (_) {
      if (!mounted) {
        return;
      }

      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Falha ao atualizar ficha')));
    } finally {
      if (mounted) {
        setState(() {
          _busyFichaId = null;
        });
      }
    }
  }

  Future<void> _deleteFicha(FichaInscricao ficha) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: const Text('Excluir ficha'),
          content: Text('Confirma excluir a ficha #${ficha.idFicha}?'),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(false),
              child: const Text('Cancelar'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(context).pop(true),
              child: const Text('Excluir'),
            ),
          ],
        );
      },
    );

    if (confirmed != true) {
      return;
    }

    setState(() {
      _busyFichaId = ficha.idFicha;
    });

    try {
      final apiClient = InheritedSessionScope.of(context).apiClient;
      await FichaApi(apiClient).delete(widget.idCatequisando, ficha.idFicha);

      if (!mounted) {
        return;
      }

      _reloadFichas();

      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Ficha excluída')));
    } on DioException catch (e) {
      if (!mounted) {
        return;
      }

      final message = extractApiError(e, fallback: 'Falha ao excluir ficha');

      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(message)));
    } catch (_) {
      if (!mounted) {
        return;
      }

      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Falha ao excluir ficha')));
    } finally {
      if (mounted) {
        setState(() {
          _busyFichaId = null;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Ficha de inscrição')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
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
              if (fichaSnapshot.connectionState == ConnectionState.waiting) {
                return const Center(child: CircularProgressIndicator());
              }

              if (fichaSnapshot.hasError) {
                return TextButton(
                  onPressed: _reloadFichas,
                  child: const Text('Falha ao carregar fichas. Tentar novamente'),
                );
              }

              final fichas = fichaSnapshot.data ?? const <FichaInscricao>[];
              if (fichas.isEmpty) {
                return const Text('Nenhuma ficha cadastrada');
              }

              final visibleCount = _visibleFichasCount > fichas.length
                  ? fichas.length
                  : _visibleFichasCount;
              final visibleFichas = fichas.take(visibleCount).toList();
              final hasMore = visibleCount < fichas.length;

              return Column(
                children: [
                  ...visibleFichas
                      .map(
                        (ficha) => Card(
                          margin: const EdgeInsets.only(bottom: 8),
                          child: ListTile(
                            title: Text('Ficha #${ficha.idFicha}'),
                            subtitle: Text(
                              'Data: ${ficha.dataInscricao}\n${ficha.observacoes}',
                            ),
                            isThreeLine: true,
                            trailing: _busyFichaId == ficha.idFicha
                                ? const SizedBox(
                                    width: 24,
                                    height: 24,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                    ),
                                  )
                                : Wrap(
                                    spacing: 4,
                                    children: [
                                      IconButton(
                                        tooltip: 'Editar',
                                        icon: const Icon(Icons.edit_outlined),
                                        onPressed: () {
                                          _editFicha(ficha);
                                        },
                                      ),
                                      IconButton(
                                        tooltip: 'Excluir',
                                        icon: const Icon(Icons.delete_outline),
                                        onPressed: () {
                                          _deleteFicha(ficha);
                                        },
                                      ),
                                    ],
                                  ),
                          ),
                        ),
                      )
                      .toList(),
                  if (hasMore)
                    Align(
                      alignment: Alignment.centerRight,
                      child: TextButton(
                        onPressed: () {
                          setState(() {
                            _visibleFichasCount += _fichasPageSize;
                          });
                        },
                        child: Text(
                          'Carregar mais (${fichas.length - visibleCount} restantes)',
                        ),
                      ),
                    ),
                ],
              );
            },
          ),
        ],
      ),
    );
  }
}
