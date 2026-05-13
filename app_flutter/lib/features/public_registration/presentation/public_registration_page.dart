import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

class PublicRegistrationPage extends StatelessWidget {
  const PublicRegistrationPage({super.key, required this.inviteToken});

  final String inviteToken;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Cadastro Público com Token'),
      ),
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 720),
          child: Card(
            child: Padding(
              padding: const EdgeInsets.all(20),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Token validado para início de cadastro', style: Theme.of(context).textTheme.titleLarge),
                  const SizedBox(height: 8),
                  Text('Token recebido: $inviteToken', style: Theme.of(context).textTheme.bodySmall),
                  const SizedBox(height: 16),
                  const Text(
                    'Etapa 2 irá conectar esta tela ao endpoint de cadastro público tokenizado. '
                    'Toda abertura desta rota exige token emitido pelo backend.',
                  ),
                  const SizedBox(height: 20),
                  Align(
                    alignment: Alignment.centerRight,
                    child: OutlinedButton(
                      onPressed: () => context.go('/login'),
                      child: const Text('Voltar ao login'),
                    ),
                  )
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
