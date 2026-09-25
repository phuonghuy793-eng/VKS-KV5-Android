# VKS KV5 Đồng Tháp — Android Source v2 (build-ready)

Bản Android WebView đóng gói nguyên trạng `vks_kv5_app_land_ui_v17.html`.

## Những gì được giữ nguyên
- 6 chức năng của v17 và toàn bộ giao diện/banner.
- GPS/WGS84, VN-2000, Đồng Tháp cũ/Tiền Giang cũ, polygon, diện tích/chu vi.
- Leaflet + Proj4 và các nguồn tra cứu trực tuyến.

## Cấu hình Android
- Application ID: `vn.dongthap.vkskv5`
- minSdk: 24
- targetSdk / compileSdk: 35
- Android Gradle Plugin: 8.7.3
- Gradle dùng để build: 8.9
- Java dùng để build: 17
- HTML: `app/src/main/assets/index.html`

## Build APK trên cloud bằng GitHub Actions (không cần máy tính)
Project có sẵn workflow `.github/workflows/build-debug-apk.yml`.

1. Tạo repository GitHub mới.
2. Upload toàn bộ nội dung thư mục source này vào repository, giữ nguyên cấu trúc thư mục.
3. Mở tab **Actions** > **Build Debug APK** > **Run workflow**.
4. Khi build hoàn tất, mở lần chạy đó và tải artifact **VKS_KV5_v1_debug**.
5. Giải nén artifact để lấy `VKS_KV5_v1_debug.apk` rồi cài trên Android để thử.

Workflow tự cài Java 17 và Gradle 8.9 trên máy build cloud, sau đó chạy `:app:assembleDebug`. Vì vậy điện thoại không cần Android Studio, Android SDK hay Gradle.

## Build trên máy có Android SDK
Nếu máy đã có Android SDK 35, Java 17 và Gradle 8.9:

```bash
gradle :app:assembleDebug
```

APK mặc định nằm tại:
`app/build/outputs/apk/debug/app-debug.apk`

## Lưu ý
- Bản đồ/vệ tinh và tra cứu web cần Internet.
- Đây là APK debug thử nghiệm, chưa phải AAB/Release cho Google Play.
- Source v1 và HTML v17 không bị ghi đè.
