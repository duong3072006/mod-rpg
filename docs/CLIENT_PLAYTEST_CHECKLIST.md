# Checklist Playtest Client — Abyssal Spire RPG `v1.0.0`

Danh sách kiểm tra nhanh trên **Minecraft Client 1.21.1 (Fabric)** cho toàn bộ hệ thống Boss, Hầm ngục 18 tầng và trang bị RPG.

---

## 0. Chuẩn Bị (2 phút)
- [ ] Cài `build/libs/abyssal-spire-rpg-1.0.0.jar` cùng **Fabric API (`>= 0.116.0`)** vào thư mục `mods/` (GeckoLib `4.8.3` đã nhúng sẵn JiJ, không cần TerraBlender).
- [ ] Mở màn hình **Mods (Mod Menu)**: kiểm tra biểu tượng `icon.png`, tên **Abyssal Spire RPG**, phiên bản **`1.0.0`**.
- [ ] Tạo **World mới** (Creative, bật Cheats) và kiểm tra tab Creative `Abyssal Spire RPG`: đầy đủ 7 block, 18 vũ khí, 7 bộ giáp (28 món), 13 trứng triệu hồi boss, không có ô vuông đen-tím (`missing texture`), hiển thị sạch cả **English (US)** ↔ **Tiếng Việt**.

---

## 1. Chiến Đấu & Hiệu Năng 5 Boss Sử Thi (5–6 phút)
Chọn khu đồng bằng thoáng hoặc vùng biển (cho Leviathan):

```mcfunction
/summon abyssalrpg:leviathan ~ ~2 ~18
/summon abyssalrpg:spirit_hydra ~ ~ ~18
/summon abyssalrpg:cerberus ~ ~ ~18
/summon abyssalrpg:nightmare_knight ~ ~ ~18
/summon abyssalrpg:jormungandr ~ ~ ~22
```
Kiểm tra cho từng con (chuyển sang `/time set midnight` để xem rõ glowmask):
- [ ] **Model & Animation:** hiển thị đầy đủ thân/đầu/cánh/đuôi, không bị biến mất đột ngột khi xoay góc nhìn, glowmask sáng đúng vùng ban đêm.
- [ ] **Phase 2 & Kỹ năng đặc trưng:** đánh hạ máu qua ngưỡng chuyển Phase — kiểm tra Spirit Hydra chặt & mọc lại 4 đầu, Cerberus phân tách 3 đầu, Nightmare Knight & Jörmungandr không phá hủy địa hình xung quanh (`terrainDamageEnabled = false`).
- [ ] **Hitbox & Sát thương:** đánh vào thân/đầu đều ăn sát thương chuẩn, BossBar giảm khớp HP, không bị NaN hay bất tử kẹt pha.
- [ ] **FPS & Dọn dẹp:** FPS giữ ổn định khi boss tung chiêu (`vfxQuality = MINIMAL`); sau khi hạ hoặc `/kill @e[type=abyssalrpg:...]`, kiểm tra không còn hitbox proxy hay entity thừa bị kẹt lại.

---

## 2. Hầm Ngục 18 Tầng & Công Trình (3 phút)
- [ ] Chạy `/abyss menu`, `/abyss enter 1`, `/abyss stats`, `/abyss exit` — xác nhận GUI, dịch chuyển và HUD RPG hoạt động bình thường.
- [ ] Chạy thử `/abyss spawnstructure citadel` và `/abyss spawntemple leviathan` (yêu cầu OP level 3+).
- [ ] Thoát world ra menu chính rồi vào lại 1 lần nữa → kiểm tra `logs/latest.log`: `0` ERROR từ `abyssalrpg` và `geckolib`.
