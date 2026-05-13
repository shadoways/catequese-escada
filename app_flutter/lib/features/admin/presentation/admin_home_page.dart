import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:app_flutter/app/app.dart';
import 'package:app_flutter/shared/widgets/app_scaffold_shell.dart';

class AdminHomePage extends StatelessWidget {
  const AdminHomePage({super.key});

  @override
  Widget build(BuildContext context) {
    final session = InheritedSessionScope.of(context).session;

    return AppScaffoldShell(
      title: 'Área Administrativa',
      subtitle: 'Gestão de usuários, permissões e configurações globais',
      onLogout: () async {
        await session.forceLogout();
        if (context.mounted) {
          context.go('/login');
        }
      },
      body: GridView.count(
        crossAxisCount: MediaQuery.of(context).size.width > 920 ? 3 : 1,
        crossAxisSpacing: 14,
        mainAxisSpacing: 14,
        childAspectRatio: 2.6,
        children: const [
          _AdminModuleCard(
            title: 'Usuários e Permissões',
            description:
                'Criar usuários, ajustar roles e controlar níveis de acesso.',
            icon: Icons.admin_panel_settings_outlined,
          ),
          _AdminModuleCard(
            title: 'Auditoria e Sessão',
            description:
                'Monitorar correlação de requisições e trilhas de operação.',
            icon: Icons.verified_user_outlined,
          ),
          _AdminModuleCard(
            title: 'Cadastros Mestre',
            description:
                'Turmas, comunidades e parâmetros de operação da paróquia.',
            icon: Icons.settings_suggest_outlined,
          ),
        ],
      ),
    );
  }
}

class _AdminModuleCard extends StatelessWidget {
  const _AdminModuleCard({
    required this.title,
    required this.description,
    required this.icon,
  });

  final String title;
  final String description;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Icon(icon, size: 26),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: Theme.of(context).textTheme.titleLarge),
                  const SizedBox(height: 6),
                  Text(
                    description,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
