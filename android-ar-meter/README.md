# Thước đo AR – VKS V5: nhiều điểm

Bản nâng cấp từ V4 dành cho đo liên tiếp A–B–C–D, không yêu cầu mọi điểm ở cùng một mặt phẳng.

- **Thêm điểm** bằng cách chạm camera hoặc bấm THÊM ĐIỂM ngay tâm ngắm.
- **LÙI 1 ĐIỂM** xóa mốc cuối; **XÓA HẾT / ĐO MỚI** bắt đầu lại.
- **KHÉP VÙNG** khi có từ 3 mốc trở lên. Xem chu vi và diện tích cho vùng đơn giản, gần đồng phẳng; nếu không đồng phẳng, diện tích hiển thị "Chưa xác định".
- Đo tối đa 24 mốc; hiển thị nhãn A-X và độ dài từng đoạn ngay trên camera, tổng chiều dài/chu vi bên dưới.
- Không đưa ra số đo khi ARCore còn mốc tạm hoặc điểm mất tracking. Cảnh báo khi kích thước dao động và khi depth hit gần chỗ đổi độ sâu; cách này không loại bỏ hết sai số.
- Độ chính xác phụ thuộc khoảng cách, ánh sáng, texture, chuyển động điện thoại và chất lượng Depth API.

**Chú ý**: Chỉ đo tham khảo, không dùng làm số liệu chứng cứ hoặc đo đạc chính thức. Nên đối chiếu với thước thật cho vật, vùng cần độ chính xác.

Biên dịch: GitHub Actions workflow **Build VKS AR V5 APK** nhánh `feature/ar-meter-v5-multipoint`.
