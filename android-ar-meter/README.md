# Thước đo AR – VKS (Android native)

Dự án ứng dụng đo khoảng cách hai điểm trên camera bằng ARCore trực tiếp (Android Java + OpenGL ES). Không sử dụng HTML/WebView, không sử dụng APK cũ làm nền.

## Tạo APK
GitHub Actions workflow: **Build VKS AR Meter APK** chạy trên nhánh \`feature/ar-meter-native-v2\` khi có thay đổi trong \`android-ar-meter/\`. File đầu ra \`app-debug.apk\` được lưu vào artifact **VKS-AR-METER-APK**. Để tự build, dùng Java 17, Android SDK 35, Gradle 8.9, chạy \`gradle :app:assembleDebug\` bên trong thư mục này.

## Thử trên máy
1. Cài APK debug và cấp quyền camera; cần Google Play Services for AR trên máy hỗ trợ ARCore.
2. Hướng camera vào bề mặt có kết cấu; chạm trên màn hình camera hoặc nhắm tâm ngắm và bấm ĐẶT ĐIỂM để đặt A.
3. Đặt B tương tự; đường nối và độ dài hiện trên camera. Bấm ĐO LẠI để bắt đầu mới.
4. Đây là phép đo AR tham khảo có sai số. Bề mặt nước, kính, vật bóng và ánh sáng yếu thường gây khó xác định điểm 3D.

**Cảnh báo:** APK có thể biên dịch thành công nhưng vẫn cần kiểm thử trên điện thoại thực. Không dùng làm chứng cứ đo đạc chính thức.
