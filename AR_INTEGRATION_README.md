# App VKS + thước đo AR native V5

Nhánh thử nghiệm `feature/app-vks-integrated-ar-v5`, xuất phát từ nhánh `main` của kho VKS-KV5-Android.

- Giữ nguyên `app/src/main/assets/index.html` (11 tiện ích cũ).
- Tạo ô **Thước đo AR** trong danh sách trang chủ với màu xanh nhạt, qua tệp `app/src/main/assets/ar_entry.js` nạp khi WebView mở trang. Tổng cộng 12 ô.
- Chạm ô mở Android Activity `vn.vks.khuvuc5.armeter.ArMeasureActivity` chạy ARCore native V5 tích hợp trực tiếp trong APK (không dùng bản APK bên ngoài).
- Camera AR đo tới 24 điểm; chiều dài từng đoạn, tổng, chu vi và diện tích gần đồng phẳng, hoàn tác và đo mới.
- Ứng dụng vẫn cài được trên thiết bị không hỗ trợ AR; riêng Thước đo AR yêu cầu Google Play Services for AR và camera hỗ trợ ARCore.
- Bản này **chưa được kiểm thử trên điện thoại**, không dùng số đo AR làm chứng cứ đo đạc chính thức.
- GitHub Actions tạo artifact **APP-VKS-WITH-AR-V5-APK**.

Nhánh `main` và mã HTML gốc không bị ghi đè bởi bản thử nghiệm này. APK mới có cùng application ID `vn.dongthap.vkskv5` với App VKS hiện tại, nên Android có thể yêu cầu gỡ bản cài bằng chữ ký khác trước khi cài.
