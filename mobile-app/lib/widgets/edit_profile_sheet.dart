import 'package:flutter/material.dart';
import '../services/auth_service.dart';

class EditProfileSheet extends StatefulWidget {
  final Map<String, dynamic> user;
  final Future<Map<String, dynamic>> Function({
    required String fullName,
    required String phone,
  })?
  onSave;
  const EditProfileSheet({super.key, required this.user, this.onSave});

  @override
  State<EditProfileSheet> createState() => _EditProfileSheetState();
}

class _EditProfileSheetState extends State<EditProfileSheet> {
  final _form = GlobalKey<FormState>();
  late final TextEditingController _name;
  late final TextEditingController _phone;
  bool _saving = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _name = TextEditingController(
      text: widget.user['fullName']?.toString() ?? '',
    );
    _phone = TextEditingController(
      text: widget.user['phone']?.toString() ?? '',
    );
  }

  @override
  void dispose() {
    _name.dispose();
    _phone.dispose();
    super.dispose();
  }

  Future<void> _save() async {
    if (_saving || !(_form.currentState?.validate() ?? false)) return;
    setState(() {
      _saving = true;
      _error = null;
    });
    try {
      final save = widget.onSave ?? AuthService.updateProfile;
      final updated = await save(
        fullName: _name.text.trim(),
        phone: AuthService.normalizePhone(_phone.text),
      );
      if (!mounted) return;
      setState(() => _saving = false);
      FocusScope.of(context).unfocus();
      Navigator.of(context).pop(updated);
    } catch (error) {
      if (!mounted) return;
      setState(() {
        _saving = false;
        _error = error.toString().replaceFirst('Exception: ', '');
      });
    }
  }

  @override
  Widget build(BuildContext context) => PopScope(
    canPop: !_saving,
    child: Padding(
      padding: EdgeInsets.fromLTRB(
        20,
        20,
        20,
        MediaQuery.viewInsetsOf(context).bottom + 20,
      ),
      child: SingleChildScrollView(
        child: Form(
          key: _form,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  const Expanded(
                    child: Text(
                      'Cập nhật thông tin cá nhân',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  IconButton(
                    onPressed: _saving
                        ? null
                        : () => Navigator.of(context).pop(),
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
              const Text(
                'Bạn có thể đổi họ tên và số điện thoại. MSSV, email và vai trò do nhà trường quản lý.',
              ),
              const SizedBox(height: 20),
              TextFormField(
                key: const ValueKey('profile-full-name-field'),
                controller: _name,
                enabled: !_saving,
                textInputAction: TextInputAction.next,
                decoration: const InputDecoration(
                  labelText: 'Họ và tên',
                  prefixIcon: Icon(Icons.person_outline),
                  border: OutlineInputBorder(),
                ),
                validator: (value) {
                  final name = value?.trim() ?? '';
                  if (name.isEmpty) return 'Vui lòng nhập họ và tên';
                  return name.length > 150
                      ? 'Họ tên không được vượt quá 150 ký tự'
                      : null;
                },
              ),
              const SizedBox(height: 14),
              TextFormField(
                key: const ValueKey('profile-phone-field'),
                controller: _phone,
                enabled: !_saving,
                keyboardType: TextInputType.phone,
                decoration: const InputDecoration(
                  labelText: 'Số điện thoại',
                  hintText: 'Ví dụ: 0356996264 hoặc +84 356 996 264',
                  prefixIcon: Icon(Icons.phone_outlined),
                  border: OutlineInputBorder(),
                ),
                validator: (value) => AuthService.isValidPhone(value ?? '')
                    ? null
                    : 'Nhập 10 chữ số bắt đầu bằng 0 hoặc mã +84',
              ),
              const SizedBox(height: 14),
              Text(
                'MSSV: ${widget.user['mssv'] ?? widget.user['studentId'] ?? 'Chưa cập nhật'}',
              ),
              Text('Email: ${widget.user['email'] ?? 'Chưa cập nhật'}'),
              Text(
                'Vai trò: ${AuthService.roleDisplayName(widget.user['roleId'] ?? widget.user['role'] ?? 'STUDENT')}',
              ),
              if (_error != null) ...[
                const SizedBox(height: 12),
                Semantics(
                  liveRegion: true,
                  child: Text(
                    _error!,
                    style: TextStyle(
                      color: Theme.of(context).colorScheme.error,
                    ),
                  ),
                ),
              ],
              const SizedBox(height: 20),
              FilledButton.icon(
                key: const ValueKey('profile-save-button'),
                onPressed: _saving ? null : _save,
                icon: _saving
                    ? const SizedBox.square(
                        dimension: 18,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.save_outlined),
                label: Text(_saving ? 'Đang lưu...' : 'Lưu thay đổi'),
              ),
            ],
          ),
        ),
      ),
    ),
  );
}
