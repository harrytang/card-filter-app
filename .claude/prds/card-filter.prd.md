# Card Filter — Chủ đề, Card và Bộ lọc

## Problem
Người dùng có những tập thông tin dạng danh sách dài (ví dụ: bảng thành phần đồ ăn dặm của Pigeon và Wakodo, tổng cộng 30 sản phẩm Pigeon và hàng chục set Wakodo, mỗi món có nguyên liệu và chất gây dị ứng riêng). Hiện thông tin nằm trong file PDF, nên muốn tìm "món nào phù hợp" phải đọc từng mục. Chi phí của việc không giải quyết: tốn thời gian, và dễ bỏ sót một mục không phù hợp khi dò bằng mắt. *(Phần "ai gặp vấn đề" và "đau ở đâu" chưa được xác nhận, xem Evidence.)*

## Evidence
- Quan sát từ 2 file PDF trong repo (`PigeonIngredients.pdf`, 9 trang; `WakodoIngredients.pdf`, 23 trang): dữ liệu có cấu trúc lặp lại (tên món, nguyên liệu gốc tiếng Nhật, bản dịch tiếng Anh, chất gây dị ứng, độ tuổi, ghi chú nhiễm chéo), nhưng **bộ thuộc tính không đồng nhất giữa hai nguồn**. Pigeon có độ tuổi và ghi chú nhiễm chéo, Wakodo gom theo set 2 món. Đây là bằng chứng về hình dạng dữ liệu, không phải bằng chứng về nhu cầu người dùng.
- Assumption — needs validation via user research: người dùng thật sự cần lọc dữ liệu kiểu này thường xuyên, và PDF/bảng tính hiện tại không đáp ứng được.

## Users
- **Primary**: TBD — needs validation via user research. Giả định ban đầu (suy ra từ nội dung PDF): người chăm sóc trẻ nhỏ cần chọn đồ ăn dặm theo chất gây dị ứng và độ tuổi. Chưa xác nhận đây là người dùng mục tiêu, hay app là công cụ tổng quát cho mọi loại bộ sưu tập.
- **Not for**: TBD — needs validation.

## Hypothesis
We believe **cho người dùng tạo chủ đề, thêm card với thuộc tính tự định nghĩa, và lọc card theo thuộc tính** will **thay thế việc đọc/dò thủ công các danh sách dài** for **người dùng có bộ dữ liệu dạng danh sách có thuộc tính (đối tượng chính: TBD)**.
We'll know we're right when **TBD — chưa có chỉ số đo được do chưa xác định người dùng mục tiêu**.

## Success Metrics
| Metric | Target | How measured |
|---|---|---|
| Thời gian từ lúc mở app đến lúc tìm ra danh sách card thoả điều kiện | TBD | TBD — needs validation via usability test |
| Tỷ lệ người dùng tạo được chủ đề đầu tiên với ≥ N card | TBD | TBD — needs validation via analytics/prototype |

## Scope
**MVP** — Chỉ gồm 4 khả năng bạn đã nêu:
1. Tạo, xem, sửa, xoá **chủ đề**.
2. Trong mỗi chủ đề có **nhiều card**.
3. Mỗi card có **thuộc tính linh động**: khi tạo card mới, người dùng tự tạo thuộc tính (tên thuộc tính) rồi nhập giá trị cho nó. Ví dụ: `name`, `thức ăn 3 tháng`, `thành phần: thịt bầm`. Hai card có thể có bộ thuộc tính khác nhau. Mỗi thuộc tính có **đúng một giá trị văn bản**.
4. **Lọc card bằng một ô tìm kiếm** trong chủ đề đang mở:
   - Khớp với **giá trị** của mọi thuộc tính trên card (không khớp tên thuộc tính), không cần chọn trước thuộc tính để lọc.
   - Nhập nhiều từ thì card hiện ra nếu chứa **một trong** các từ.
   - Không phân biệt hoa/thường và dấu tiếng Việt (ví dụ `thit bam` khớp `thịt bầm`).
   - Có xử lý riêng cho tiếng Nhật: tìm hiragana khớp katakana và ngược lại. Không khớp kanji với cách đọc.
   - Chỉ tìm trong một chủ đề, không tìm xuyên các chủ đề.
5. Dữ liệu **lưu cục bộ** trên thiết bị của người dùng, không cần tài khoản.
6. Là **ứng dụng di động cho iOS và Android**.

**Out of scope** *(đề xuất, cần bạn xác nhận hoặc điều chỉnh)*
- Nhập tự động dữ liệu từ PDF — chưa rõ có cần không; MVP nhập tay để kiểm chứng mô hình dữ liệu trước.
- Đưa ra lời khuyên y tế/dị ứng — app chỉ lọc theo dữ liệu người dùng tự nhập, không đảm bảo an toàn.
- Chia sẻ chủ đề / nhiều người dùng / đồng bộ giữa thiết bị — dữ liệu chỉ lưu cục bộ nên không có trong MVP.
- Bộ lọc nâng cao (loại trừ, khoảng số, kết hợp AND/OR, lọc theo từng thuộc tính) — bạn đã chọn ô tìm kiếm đơn giản cho MVP. Đề xuất ghi nhận là ngoài phạm vi, cần xác nhận.
- Khớp kanji với cách đọc (ví dụ `とり` ra `鶏`) — bạn đã quyết định không làm.
- Phiên bản web hoặc máy tính — MVP chỉ nhắm iOS và Android.
- Kiểu dữ liệu cho thuộc tính (số, ngày, danh sách chọn) — hiện chỉ có tên thuộc tính và giá trị nhập tay; kiểu giá trị chưa được yêu cầu.

## Delivery Milestones
<!-- Business outcomes, not engineering tasks. /plan turns each into a plan. -->
<!-- Status: pending | in-progress | complete -->

| # | Milestone | Outcome | Status | Plan |
|---|---|---|---|---|
| 1 | Chủ đề và card cơ bản | Người dùng tạo được chủ đề và thêm card vào đó | in-progress | .claude/plans/card-filter-milestone-1.plan.md |
| 2 | Thuộc tính linh động | Mỗi card mang các thuộc tính do người dùng tự định nghĩa | in-progress | .claude/plans/card-filter-milestone-2.plan.md |
| 3 | Lọc card | Người dùng thu hẹp danh sách card theo thuộc tính | in-progress | .claude/plans/card-filter-milestone-3.plan.md |

## Open Questions
- [ ] Đối tượng người dùng chính là ai (phụ huynh có con bị dị ứng, hay công cụ tổng quát)? Câu trả lời quyết định cách ưu tiên bộ lọc.
- [x] ~~Thuộc tính linh động là gì?~~ Đã rõ: khi tạo card, người dùng tạo thuộc tính (tên) rồi nhập giá trị; mỗi thuộc tính một giá trị văn bản (nhiều nguyên liệu thì gõ chung trong một chuỗi).
- [x] ~~Bộ lọc cần những kiểu nào?~~ Đã rõ: một ô tìm kiếm, khớp với mọi thông tin trên card.
- [x] ~~Khớp giá trị hay tên thuộc tính?~~ Chỉ khớp giá trị.
- [x] ~~Nhiều từ khoá: tất cả hay một trong?~~ Một trong các từ.
- [x] ~~Phân biệt hoa/thường và dấu?~~ Không phân biệt (`thit bam` khớp `thịt bầm`). Có yêu cầu riêng cho tiếng Nhật: hiragana khớp katakana; không làm khớp kanji.
- [x] ~~Trong một chủ đề hay xuyên chủ đề?~~ Trong một chủ đề.
- [ ] Có cần nhập dữ liệu từ 2 PDF vào app không, hay PDF chỉ là dữ liệu mẫu để thiết kế?
- [ ] Dữ liệu gốc có nhiều ngôn ngữ (Nhật và Anh trong PDF); app có cần hỗ trợ nội dung đa ngôn ngữ không?
- [x] ~~Nơi lưu dữ liệu?~~ Cục bộ, không tài khoản.
- [x] ~~Nền tảng?~~ Ứng dụng di động iOS và Android.
- [ ] Dữ liệu chỉ lưu cục bộ nên gỡ app hoặc đổi điện thoại sẽ mất card. Có cần xuất/nhập (sao lưu) trong MVP không?

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Ô tìm kiếm chỉ **khớp để hiện ra**, không **loại trừ**. Gõ "trứng" cho ra món có trứng, nhưng không cho biết món nào không có trứng. Nếu người dùng muốn tìm món an toàn cho trẻ dị ứng thì MVP không phục vụ được, và có thể bị hiểu nhầm là phục vụ được | High | High | Ghi rõ trong sản phẩm đây là tìm kiếm khớp từ khoá, không phải bộ lọc an toàn; xác nhận với bạn có cần loại trừ ở bản sau không (xem Out of scope) |
| Người dùng tin vào kết quả tìm kiếm liên quan dị ứng, nhưng dữ liệu nhập tay sai hoặc thiếu | Medium | High | Ghi rõ app không thay thế việc đọc nhãn thật; có hiển thị cảnh báo hay không (TBD) |
| Tên thuộc tính tự do khiến dữ liệu thiếu nhất quán giữa các card (ví dụ "thành phần" và "nguyên liệu" cùng nghĩa), tìm kiếm bỏ sót | Medium | Medium | Ô tìm kiếm khớp mọi thông tin giúp giảm rủi ro này; kiểm chứng bằng prototype với dữ liệu PDF |
| Yêu cầu chưa được xác thực (người dùng, nhu cầu, chỉ số đều TBD) nên MVP có thể xây sai thứ | High | High | Chạy xác thực nhanh với 1-2 người dùng thật hoặc prototype trước khi đầu tư lớn |

---
*Status: DRAFT — requirements only. Implementation planning pending via /plan.*
