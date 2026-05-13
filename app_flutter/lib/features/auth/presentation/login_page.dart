import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import 'package:app_flutter/app/app.dart';

class LoginPage extends StatefulWidget {
  const LoginPage({super.key});

  @override
  State<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends State<LoginPage> {
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _publicTokenController = TextEditingController();

  bool _submitting = false;
  String? _errorMessage;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _publicTokenController.dispose();
    super.dispose();
  }

  Future<void> _onLogin() async {
    final session = InheritedSessionScope.of(context).session;

    setState(() {
      _submitting = true;
      _errorMessage = null;
    });

    try {
      await session.signIn(
        email: _emailController.text,
        password: _passwordController.text,
      );
      if (!mounted) {
        return;
      }
      context.go('/');
    } catch (e) {
      setState(() {
        _errorMessage = e.toString().replaceFirst('Exception: ', '');
      });
    } finally {
      if (mounted) {
        setState(() {
          _submitting = false;
        });
      }
    }
  }

  void _goPublicRegistration() {
    final token = _publicTokenController.text.trim();
    if (token.isEmpty) {
      setState(() {
        _errorMessage = 'Informe o token de cadastro público';
      });
      return;
    }
    context.go('/cadastro-publico?token=$token');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 460),
          child: Card(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Catequese CRM', style: Theme.of(context).textTheme.headlineMedium),
                  const SizedBox(height: 6),
                  Text(
                    'Acesso seguro ao sistema interno da paróquia.',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                  const SizedBox(height: 24),
                  TextField(
                    controller: _emailController,
                    keyboardType: TextInputType.emailAddress,
                    decoration: const InputDecoration(labelText: 'Email'),
                  ),
                  const SizedBox(height: 12),
                  TextField(
                    controller: _passwordController,
                    obscureText: true,
                    decoration: const InputDecoration(labelText: 'Senha'),
                  ),
                  if (_errorMessage != null) ...[
                    const SizedBox(height: 10),
                    Text(_errorMessage!, style: const TextStyle(color: Colors.redAccent)),
                  ],
                  const SizedBox(height: 16),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton(
                      onPressed: _submitting ? null : _onLogin,
                      child: Text(_submitting ? 'Entrando...' : 'Entrar'),
                    ),
                  ),
                  const Divider(height: 28),
                  TextField(
                    controller: _publicTokenController,
                    decoration: const InputDecoration(
                      labelText: 'Token de cadastro público',
                      hintText: 'Token emitido pelo administrador',
                    ),
                  ),
                  const SizedBox(height: 10),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton(
                      onPressed: _goPublicRegistration,
                      child: const Text('Acessar cadastro público com token'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
