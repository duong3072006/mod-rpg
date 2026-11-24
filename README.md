# Abyssal Spire RPG (`v1.0.0` — Minecraft 1.21.1 Fabric)

**Abyssal Spire RPG** là bản mở rộng chuyên biệt về **13 Đại Boss sử thi đa giai đoạn** cho **Minecraft 1.21.1 (Fabric)**, gồm **6 Boss GeckoLib `4.8.3`** (Cinder Dragon, Leviathan, Spirit Hydra, Cerberus, Nightmare Knight, Jörmungandr), **7 Boss điêu khắc 3D** (3 Core Boss + 4 Mythological Titan), **7 Đền Triệu Hồi Mythic** trên Overworld và **4 quần xã gốc**.

---

## Điểm Nổi Bật Chính

- **6 Boss Sử Thi Đa Giai Đoạn (GeckoLib 4.8.3 JiJ):**
  - **Cinder Dragon — Hỏa Long Giáp Đá** (`2,700 → 4,590 HP`, Def `20 → 34`, 6 proxy hitboxes, 10 kỹ năng lửa & không chiến), **Leviathan** (`1,500 → 2,250 HP`), **Spirit Hydra** (`2,000 → 3,000 HP`, 4 đầu chặt/mọc lại), **Cerberus** (`2,000 → 3,000 HP`, phân thân 3 đầu), **Nightmare Knight** (`4,000 → 7,200 HP`, phòng ngự vượt trần vanilla), **Jörmungandr** (`1,800 → 2,520 HP`, rắn khổng lồ đa đoạn).
  - Tất cả mặc định `vfxQuality = MINIMAL` và `terrainDamageEnabled = false` (chống lag, không phá hủy công trình người chơi).
- **7 Boss Điêu Khắc 3D & 7 Đền Triệu Hồi Mythic:** Abyssal Fenrir Behemoth, Abyssal Bone Widow, Volcanic Magma Hydra, Quetzalcoatl, Apep, Fenrir, Tiamat cùng 7 Đền Triệu Hồi Mythic (`abyssalrpg:mythic_temple` + `/abyss spawntemple <god>`) và 4 quần xã gốc (`Abyssal Wasteland`, `Ashen Volcanic Peaks`, `Jade Mythic Jungle`, `Frozen Ragnarok Tundra`) không cần TerraBlender.
- **BossBar HUD Riêng & 13 Trứng Triệu Hồi + 7 Cổ Vật Boss:** thanh máu Boss trang trí riêng, hiệu ứng rung màn hình theo đòn đánh, đủ 13 Spawn Egg và 7 cổ vật chiến lợi phẩm boss.
- **Bản địa hóa 100% song ngữ EN / VI:** khớp khóa tuyệt đối EN / VI (0 khóa thiếu, 0 khóa thô).

---

## Yêu Cầu Cài Đặt

| Thành phần | Phiên bản yêu cầu | Ghi chú |
|---|---|---|
| **Minecraft** | `1.21.1` | Chạy được cả Client & Dedicated Server (`environment: "*"`) |
| **Java** | `>= 21` | Khuyến nghị Java 21 LTS |
| **Fabric Loader** | `>= 0.16.0` | Đã kiểm định trên `0.20.1` |
| **Fabric API** | `>= 0.116.0` | Bắt buộc cài trong `mods/` (đã kiểm định `0.116.18+1.21.1`) |
| **GeckoLib (Fabric)** | `>= 4.8.0` | Đã nhúng sẵn Jar-in-Jar (`4.8.3`), tương thích nếu cài bản `>= 4.8.0` |

---

## Cấu Hình Boss

Sau lần khởi động đầu tiên, mod tự động sinh 6 file cấu hình boss trong thư mục `config/`:
- `config/abyssalrpg_cinder_dragon.json`
- `config/abyssalrpg_leviathan.json`
- `config/abyssalrpg_spirit_hydra.json`
- `config/abyssalrpg_cerberus.json`
- `config/abyssalrpg_nightmare_knight.json`
- `config/abyssalrpg_jormungandr.json`

Xem thêm: [CHANGELOG.md](CHANGELOG.md).

---

## Build Từ Mã Nguồn (Nhà Phát Triển)

```powershell
# Biên dịch kiểm tra chặt không cảnh báo (-Werror)
.\gradlew.bat clean -I werror.init.gradle compileJava compileClientJava build --rerun-tasks --no-daemon
```

## Giấy Phép

Phát hành theo giấy phép **MIT License** — xem chi tiết tại [LICENSE](LICENSE).
