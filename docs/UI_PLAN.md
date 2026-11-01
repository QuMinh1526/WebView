# Kế hoạch UI

## Phong cách

- Nền canvas đen `#000000`, bề mặt đặc tối `#13161B`, chữ chính trắng.
- Xanh `#006ED5` và `#317AFF` dành cho hành động/chọn; tint xanh alpha thấp chỉ dùng cục bộ.
- Acrylic phẳng: nền đặc, viền tint xanh 1 dp và bóng mềm; backdrop blur luôn 0 dp.
- Dùng IBM Plex Sans cho nội dung, Metropolis cho tiêu đề; dự phòng bằng font hệ thống.
- Dùng họ khoảng cách 8/16/24 dp; góc 8 dp, pill 32 dp, sheet 16 dp.
- Control giữ vùng chạm tối thiểu 48 dp; surface và viền tint giống nhau trên API cũ.
- Press dùng spring trong spec; chuyển màn fade tuyến tính 150 ms, có xét reduced motion.
- Thang chữ đầy đủ dùng cỡ Material 3 mặc định cho các mục spec chưa định cỡ (suy luận).

## Component đã có trong spec

- Acrylic surface/panel, icon tile, action button, search field.
- Switch, slider, checkbox, filter chip.
- Bottom app bar, collapsible app bar và bottom sheet.

## Component trình duyệt chưa có trong spec

- Thanh URL: tái dùng SearchField 40 dp dạng pill, inset ngang 24 dp (suy luận).
- Tab strip/switcher: chip trong hàng 48 dp, cách nhau 8 dp (suy luận).
- Dialog quyền mic/camera: panel/sheet surface đặc, góc 16 dp, viền 1 dp (suy luận).
- Trang lỗi/SSL: panel 8 dp, chữ tương phản cao, nút action theo token (suy luận).
- Màn Cài đặt: hàng control theo spacing 8/16/24 dp và hit target 48 dp (suy luận).
- Menu ba chấm: sheet nền raised, góc trên 16 dp (suy luận).
- Snackbar: surface raised, viền 1 dp và action xanh (suy luận).
- Dialog xác nhận tải xuống: panel 8 dp, action button xanh và nút trung tính (suy luận).

## Cách suy ra

Giữ nguyên palette, font, shadow, góc bo và motion đã nêu; không thêm token màu hay blur. Những component mới chỉ tổ hợp lại AcrylicPanel, SearchField, Chip, ActionButton và BottomSheet. Kích thước/spacing không có trong spec sẽ chỉ dùng các giá trị đã có ở component gần nhất hoặc các nấc 8/16/24 dp; các quyết định suy ra được đánh dấu “(suy luận)”.
