# WebView Browser

Trình duyệt Android một Activity, viết bằng Kotlin và Jetpack Compose Material 3. UI tuân theo `docs/DESIGN_SPEC.md`; quy tắc thiết kế nằm trong `AGENTS.md`.

## Môi trường

- JDK 21; Gradle được ghim vào `/usr/lib/jvm/java-21-openjdk-amd64` trong `gradle.properties`.
- Nếu shell đang dùng JDK khác: `export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64` và `export PATH="$JAVA_HOME/bin:$PATH"`.
- Android SDK command-line tools, API 36 và Build Tools 36.0.0.
- Cấu hình `local.properties` với `sdk.dir=/đường/dẫn/Android/Sdk` (đã ignore, không commit). Có thể cài SDK cần thiết bằng:

  ```sh
  sdkmanager --licenses
  sdkmanager --install platform-tools 'platforms;android-36' 'build-tools;36.0.0'
  ```

- Build chuẩn: `./gradlew clean assembleDebug test lint`
- Kiểm tra bản release/R8: `./gradlew assembleRelease`

## Ký và phát hành APK

- Tạo khóa release một lần tại `keystore/release.jks` và thông tin tại `keystore.properties`. Các file này, keystore `.b64`, và `dist/` đều được ignore; không commit/chia sẻ mật khẩu. Không tạo lại/ghi đè khóa nếu đã có bản phát hành.
- Sao lưu `keystore/release.jks.b64` vào thiết bị cá nhân được mã hoá/offline, rồi xác minh có thể khôi phục từ bản sao. Mất khóa đồng nghĩa không thể ký bản cập nhật cài đè. Không tải bản sao riêng tư này lên repo hay gửi qua kênh chat.
- Tạo APK đã zipalign/ký/verify cùng SHA-256: `./scripts/release.sh`. Script đọc thông tin ký từ `keystore.properties`, lấy build-tools mới nhất từ Android SDK và xuất `dist/WebView-vX.Y.Z.apk`.
- Chứng chỉ trong APK là self-signed; so khớp SHA-256 certificate fingerprint khi xác minh bản cài đặt. Mật khẩu ký yếu làm giảm khả năng bảo vệ khóa; thay bằng mật khẩu ngẫu nhiên mạnh và cập nhật properties an toàn trước khi phân phối rộng.
- Cài debug APK: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Cấu trúc

- `browser/`: ViewModel, state tab, URL/policy/UA, WebView pool, quyền trang và script injector.
- `data/`: cài đặt DataStore và quyền đã cấp theo origin.
- `ui/browser/`: màn hình trình duyệt và dialog; `ui/components/`, `ui/theme/`: component và token giao diện.
- `app/src/main/res/xml/`: cấu hình network security và loại trừ backup.
- `app/src/test/`: unit test cho URL, scheme, allowlist xác minh và User-Agent.

## Bảo mật và giới hạn

- Chỉ tải nội dung web qua HTTP(S); cleartext bị chặn ở cấu hình mạng. Không cho phép file/content access, mixed content, lưu form data hoặc SSL proceed; Safe Browsing bật.
- Popup và external scheme yêu cầu user gesture/xác nhận. Mic, camera và geolocation cần xác nhận người dùng cùng quyền Android; quyền lưu theo origin và có thể thu hồi trong Settings.
- Cookie không được log; third-party cookie bật mặc định để hỗ trợ đăng nhập/SSO. Dữ liệu backup bị tắt. Multi-profile WebView tách tab riêng tư khi API có hỗ trợ; fallback xoá cookie và storage dùng chung khi đóng tab riêng tư cuối.
- Release bật R8/minify; đã xác nhận bằng `./gradlew assembleRelease`.
- Không có `addJavascriptInterface`, không giả mạo `navigator.*`, không giải/vượt CAPTCHA hay tự động hoá tương tác. Script injector hiện no-op, chỉ nhận main frame và origin allowlist cụ thể.
- UA dựa trên WebView cài đặt; desktop mode dùng phiên bản Chrome có trong UA đó. WebView dưới major 120 hiện cảnh báo cập nhật.
- Trang có biện pháp chống bot mạnh vẫn có thể chặn WebView. Khi gặp trường hợp đó, hãy mở trang bằng Chrome Custom Tabs; đăng nhập Google cũng được chuyển sang Custom Tabs.
- Diagnostic screen chỉ có trong debug build. Header thật, CAPTCHA, đăng nhập, tải xuống, quyền thiết bị và hành vi SSL cần xác nhận trên emulator/device; chưa được xác minh bằng chạy tay trong môi trường hiện tại.
- `lint` hiện thành công nhưng giữ 9 cảnh báo: 8 gợi ý nâng Gradle/dependency (các phiên bản đang dùng đã được build cùng nhau) và cảnh báo JavaScript cần thiết cho trình duyệt. Không tắt lint; không thêm JavaScript bridge. Các API Profile có kiểm tra `MULTI_PROFILE` ở runtime và các WebSettings deprecated được giữ vì yêu cầu hardening, với suppression hẹp có giải thích tại code.

## Checklist kiểm thử thủ công

- Đăng nhập vào một trang, thoát hẳn ứng dụng, mở lại và kiểm tra phiên đăng nhập còn hiệu lực.
- Bật/tắt desktop mode và kiểm tra trang tải lại, viewport/zoom thay đổi đúng.
- HTTP cleartext và trang có chứng chỉ TLS lỗi phải bị chặn, không có tuỳ chọn tiếp tục.
- Mở yêu cầu mic/camera/geolocation; kiểm tra origin, lựa chọn một lần/luôn cho phép/từ chối và thu hồi quyền.
- Trong Diagnostics (debug), mở `https://httpbin.org/headers`; kiểm tra request không có `X-Requested-With`.
- Hoàn tất CAPTCHA trên trang thử nghiệm hợp pháp; xác minh resource xác minh không bị domain blocklist chặn.
- Kiểm tra mở/đóng/chuyển tab, popup có user gesture, pull-to-refresh, upload, download sau xác nhận, fullscreen video và giữ state khi xoay.
