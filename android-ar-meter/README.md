# Thước đo AR – VKS V4 · Đa vật thể

Dùng ARCore native. Thay đổi lớn: không khóa điểm A/B trên cùng một mặt phẳng như V3.

- Ưu tiên điểm chiều sâu để chọn trên đồ vật không phẳng; sau đó mặt phẳng; sau đó điểm đặc trưng.
- Khi AR chưa nhận diện 3D vẫn đặt mốc tạm Instant Placement. **Không hiển thị số đo cho đến khi cả hai mốc có dữ liệu theo dõi đầy đủ**, vì khoảng cách tạm 1 mét chỉ là giả định.
- Đo độ dài thẳng theo không gian 3D, không phải đường cong trên bề mặt; chỉ là kết quả tham khảo.
- Khi nhắm vào bề mặt kính, nước, vật sáng bóng, vật trơn hoặc thiếu sáng, phép đo có thể sai hay chưa xác định được.

Build: workflow `Build VKS AR Meter APK` trên nhánh `feature/ar-meter-v4-multi-object`; dùng GitHub Actions, Java 17, Gradle 8.9, Android SDK 35.
