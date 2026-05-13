import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:app_flutter/app/app.dart';
import 'package:app_flutter/features/catequisandos/data/catequisando_api.dart';
import 'package:app_flutter/shared/widgets/app_scaffold_shell.dart';

class OperationalHomePage extends StatefulWidget {
  const OperationalHomePage({super.key});

  @override
  State<OperationalHomePage> createState() => _OperationalHomePageState();
}

class _OperationalHomePageState extends State<OperationalHomePage> {
  late Future<List<CatequisandoSummary>> _catequisandosFuture;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _catequisandosFuture = _loadCatequisandos();
  }

  Future<List<CatequisandoSummary>> _loadCatequisandos() {
    final apiClient = InheritedSessionScope.of(context).apiClient;
    return CatequisandoApi(apiClient).fetchAll();
  }

  @override
  Widget build(BuildContext context) {
    final session = InheritedSessionScope.of(context).session;

    return AppScaffoldShell(
      title: 'Área Operacional',
      subtitle: 'Cadastro, fichas, presença e consultas do dia a dia',
      onLogout: () async {
        await session.forceLogout();
        if (context.mounted) {
          context.go('/login');
        }
      },
      body: ListView(
        children: [
          _OperationalTile(
            icon: Icons.people_alt_outlined,
            title: 'Catequisandos',
            subtitle:
                'Consultar, atualizar dados e navegar pelo histórico de fichas.',
            onTap: () {
              context.push('/operacional/catequisandos');
            },
          ),
          FutureBuilder<List<CatequisandoSummary>>(
            future: _catequisandosFuture,
            builder: (context, snapshot) {
              if (snapshot.connectionState == ConnectionState.waiting) {
                return const Padding(
                  padding: EdgeInsets.symmetric(vertical: 16),
                  child: Center(child: CircularProgressIndicator()),
                );
              }

              if (snapshot.hasError) {
                return Card(
                  margin: const EdgeInsets.only(bottom: 12),
                  child: ListTile(
                    leading: const Icon(Icons.warning_amber_outlined),
                    title: const Text('Falha ao carregar catequisandos'),
                    subtitle: Text(snapshot.error.toString()),
                    trailing: TextButton(
                      onPressed: () {
                        setState(() {
                          _catequisandosFuture = _loadCatequisandos();
                        });
                      },
                      child: const Text('Tentar novamente'),
                    ),
                  ),
                );
              }

              final list = snapshot.data ?? const <CatequisandoSummary>[];
              final totalAtivos = list.where((item) => item.ativo).length;
              final totalDocumentos = list.fold<int>(
                0,
                (sum, item) => sum + item.documentosTotal,
              );

              return Card(
                margin: const EdgeInsets.only(bottom: 12),
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Resumo em tempo real',
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 8),
                      Text('Total de catequisandos: ${list.length}'),
                      Text('Ativos: $totalAtivos'),
                      Text('Documentos vinculados: $totalDocumentos'),
                    ],
                  ),
                ),
              );
            },
          ),
          _OperationalTile(
            icon: Icons.fact_check_outlined,
            title: 'Fichas de Cadastro',
            subtitle:
                'Cadastrar novas fichas por ano e manter histórico por catequisando.',
          ),
          _OperationalTile(
            icon: Icons.how_to_reg_outlined,
            title: 'Presença',
            subtitle:
                'Marcação por turma e data com visão rápida de ausências.',
          ),
          _OperationalTile(
            icon: Icons.event_note_outlined,
            title: 'Eventos',
            subtitle: 'Cadastro e consulta de eventos com foco pastoral.',
          ),
          _OperationalTile(
            icon: Icons.folder_shared_outlined,
            title: 'Documentos',
            subtitle:
                'Upload e atualização de documentos com regras de segurança.',
          ),
        ],
      ),
    );
  }
}

class _OperationalTile extends StatelessWidget {
  const _OperationalTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: ListTile(
        contentPadding: const EdgeInsets.symmetric(
          horizontal: 16,
          vertical: 12,
        ),
        leading: Icon(icon, size: 26),
        title: Text(title, style: Theme.of(context).textTheme.titleLarge),
        subtitle: Padding(
          padding: const EdgeInsets.only(top: 6),
          child: Text(subtitle),
        ),
        trailing: const Icon(Icons.chevron_right),
        onTap: onTap,
      ),
    );
  }
}
