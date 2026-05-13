// This is a basic Flutter widget test.
//
// To perform an interaction with a widget in your test, use the WidgetTester
// utility in the flutter_test package. For example, you can send tap and scroll
// gestures. You can also use WidgetTester to find child widgets in the widget
// tree, read text, and verify that the values of widget properties are correct.

import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'package:app_flutter/app/app.dart';

void main() {
  testWidgets('Login screen renders', (WidgetTester tester) async {
    FlutterSecureStorage.setMockInitialValues(const <String, String>{});

    await tester.pumpWidget(const CatequeseApp());
    await tester.pumpAndSettle();

    expect(find.text('Catequese CRM'), findsOneWidget);
    expect(find.text('Entrar'), findsOneWidget);
  });
}
