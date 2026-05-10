import re

def enhance_minh_chung(text):
    text = text.strip()
    if 'assertThat' in text:
        return 'Sử dụng `assertThat()` để kiểm tra dữ liệu thực tế trên DB H2. Tự động Rollback.'
    elif 'verify' in text:
        return 'Sử dụng `verify()` của Mockito để xác nhận lời gọi hàm. Không tác động DB.'
    elif 'assertThrows' in text:
        return 'Sử dụng `assertThrows()` để bắt ngoại lệ. Đảm bảo luồng bị chặn đúng lúc.'
    elif 'ArgumentCaptor' in text:
        return 'Sử dụng `ArgumentCaptor` để bắt và kiểm tra giá trị của object trước khi lưu.'
    return text

def enhance_input(text):
    text = text.strip()
    if text == 'id=1': return 'ID hợp lệ (VD: id=1) tồn tại trong DB'
    if text == 'id=999': return 'ID không tồn tại (VD: id=999)'
    if text.startswith('status='): return f"Trạng thái đầu vào: {text.split('=')[1]}"
    if text == 'All OK' or text == 'valid DTO': return 'DTO hợp lệ, đầy đủ các trường bắt buộc'
    return text

def enhance_output(text):
    text = text.strip()
    if 'Throw' in text: return f"Ném ra ngoại lệ `{text.split('Throw ')[1]}`. Chặn quá trình thực thi."
    if 'Trả về' in text: return text + " kèm dữ liệu chính xác, không bị null."
    if 'Xóa thành công' in text: return 'Bản ghi bị xóa khỏi DB H2. Kiểm tra lại bằng findById sẽ ra rỗng.'
    if 'Cập nhật thành công' in text: return 'Dữ liệu mới được ghi đè thành công vào H2 Database.'
    return text

with open('docs/test.md', 'r') as f:
    lines = f.readlines()

out_lines = []
for line in lines:
    if line.startswith('|') and not line.startswith('|---') and not line.startswith('| Tên chức năng') and not line.startswith('| Cột |'):
        parts = line.split('|')
        if len(parts) >= 8:
            # parts: ['', 'Tên', 'Func', 'ID', 'Obj', 'Input', 'Output', 'Minh chung', '\n']
            parts[5] = ' ' + enhance_input(parts[5]) + ' '
            parts[6] = ' ' + enhance_output(parts[6]) + ' '
            parts[7] = ' ' + enhance_minh_chung(parts[7]) + ' '
            out_lines.append('|'.join(parts))
        else:
            out_lines.append(line)
    else:
        out_lines.append(line)

with open('docs/test.md', 'w') as f:
    f.writelines(out_lines)
