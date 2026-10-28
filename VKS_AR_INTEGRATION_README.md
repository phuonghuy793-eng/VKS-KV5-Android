# App VKS + Thước đo AR V5 — bản thử riêng

- Dựa trên App VKS KV5 gốc và giữ nguyên HTML/assets của 11 tiện ích hiện tại.
- Thước đo AR hiển thị ngay sau Sơ đồ vụ việc, trước Tính lãi.
- Giao diện bên trong giữ cùng các lớp hero, page, card, back, calc với tiện ích cũ.
- Nút MỞ CAMERA ĐO AR gọi AndroidAR.openCamera() từ WebView sang Activity Android ARCore gốc.
- ARCore V5 hỗ trợ nhiều điểm A-B-C..., các đoạn, tổng chiều dài, chu vi, diện tích, hoàn tác.
- File HTML index.html không thay đổi. Menu AR thêm qua app/src/main/assets/vks_ar_integration.js.
- APK có applicationId thử nghiệm vn.dongthap.vkskv5.arpreview, cài song song với app cũ.
- Không dùng kết quả AR làm số đo chuyên môn khi chưa đối chiếu bằng dụng cụ thật.
- Workflow: .github/workflows/vks-app-ar-preview.yml trên feature/vks-app-ar-v5-integration.
