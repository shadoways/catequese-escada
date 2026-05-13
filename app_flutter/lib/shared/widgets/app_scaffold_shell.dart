import 'package:flutter/material.dart';

import 'package:app_flutter/app/theme/app_colors.dart';

class AppScaffoldShell extends StatelessWidget {
  const AppScaffoldShell({
    super.key,
    required this.title,
    required this.subtitle,
    required this.body,
    required this.onLogout,
    this.actions = const [],
  });

  final String title;
  final String subtitle;
  final Widget body;
  final VoidCallback onLogout;
  final List<Widget> actions;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title),
            Text(
              subtitle,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
        actions: [
          ...actions,
          IconButton(
            onPressed: onLogout,
            tooltip: 'Sair',
            icon: const Icon(Icons.logout_rounded, color: AppColors.textPrimary),
          ),
        ],
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: body,
        ),
      ),
    );
  }
}
