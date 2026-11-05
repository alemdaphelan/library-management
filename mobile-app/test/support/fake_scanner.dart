import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

/// UI-only scanner: no camera access, permission prompts or barcode recognition.
class UiFakeScanner extends MobileScannerPlatform {
  @override
  Stream<BarcodeCapture?> get barcodesStream => const Stream.empty();
  @override
  Stream<TorchState> get torchStateStream => const Stream.empty();
  @override
  Stream<double> get zoomScaleStateStream => const Stream.empty();
  @override
  Widget buildCameraView() => const ColoredBox(
    color: Color(0xFF172334),
    child: Center(
      child: Text('Camera giả lập', style: TextStyle(color: Colors.white)),
    ),
  );
  @override
  Future<MobileScannerViewAttributes> start(StartOptions options) async =>
      const MobileScannerViewAttributes(
        cameraDirection: CameraFacing.back,
        currentTorchMode: TorchState.off,
        size: Size(640, 480),
        numberOfCameras: 1,
      );
  @override
  Future<void> stop() async {}
  @override
  Future<void> pause() async {}
  @override
  Future<void> dispose() async {}
  @override
  Future<void> updateScanWindow(Rect? window) async {}
  @override
  Future<void> resetZoomScale() async {}
}
