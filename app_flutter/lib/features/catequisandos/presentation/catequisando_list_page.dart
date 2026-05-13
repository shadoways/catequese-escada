import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:app_flutter/app/app.dart';
import 'package:app_flutter/features/catequisandos/data/catequisando_api.dart';

class CatequisandoListPage extends StatefulWidget {
  const CatequisandoListPage({super.key});

  @override
  State<CatequisandoListPage> createState() => _CatequisandoListPageState();
}

class _CatequisandoListPageState extends State<CatequisandoListPage> {
  final TextEditingController _searchController = TextEditingController();
  late Future<List<CatequisandoSummary>> _future;
  List<CatequisandoSummary> _items = const <CatequisandoSummary>[];

  @override
  void initState() {
    super.initState();
    _searchController.addListener(() {
      setState(() {});
    });
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _future = _load();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<List<CatequisandoSummary>> _load() async {
    final apiClient = InheritedSessionScope.of(context).apiClient;
    final items = await CatequisandoApi(apiClient).fetchAll();
    _items = items;
    return items;
  }

  @override
  Widget build(BuildContext context) {
    final query = _searchController.text.trim().toLowerCase();

    return Scaffold(
      appBar: AppBar(title: const Text('Catequisandos')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            TextField(
              controller: _searchController,
              decoration: const InputDecoration(
                prefixIcon: Icon(Icons.search),
                labelText: 'Buscar por nome',
              ),
            ),
            const SizedBox(height: 12),
            Expanded(
              child: FutureBuilder<List<CatequisandoSummary>>(
                future: _future,
                builder: (context, snapshot) {
                  if (snapshot.connectionState == ConnectionState.waiting) {
                    return const Center(child: CircularProgressIndicator());
                  }

                  if (snapshot.hasError) {
                    return Center(
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const Text('Falha ao carregar catequisandos'),
                          const SizedBox(height: 8),
                          TextButton(
                            onPressed: () {
                              setState(() {
                                _future = _load();
                              });
                            },
                            child: const Text('Tentar novamente'),
                          ),
                        ],
                      ),
                    );
                  }

                  final source = snapshot.data ?? _items;
                  final filtered = query.isEmpty
                      ? source
                      : source
                            .where(
                              (item) => item.nome.toLowerCase().contains(query),
                            )
                            .toList();

                  if (filtered.isEmpty) {
                    return const Center(
                      child: Text('Nenhum catequisando encontrado'),
                    );
                  }

                  return ListView.separated(
                    itemCount: filtered.length,
                    separatorBuilder: (_, _) => const Divider(height: 1),
                    itemBuilder: (context, index) {
                      final item = filtered[index];
                      return ListTile(
                        title: Text(item.nome),
                        subtitle: Text(item.ativo ? 'Ativo' : 'Inativo'),
                        trailing: const Icon(Icons.chevron_right),
                        onTap: () {
                          context.push('/operacional/catequisandos/${item.id}');
                        },
                      );
                    },
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
