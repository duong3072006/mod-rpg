package com.abyssalrpg.client.gui;

import com.abyssalrpg.item.TogetsuGlowStyle;
import com.abyssalrpg.item.TogetsuItem;
import com.abyssalrpg.network.TogetsuColorC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Giao diện chọn & tự pha màu điểm sáng nổi 3D cho **Togetsu (Song Đao & Song Nguyệt)**:
 * - Mở nhanh bằng phím **`[C]`** khi đang cầm Togetsu (có thể đổi phím trong Controls).
 * - Đủ 6 Preset (4 Option chuẩn + Ngọc Lục Bảo + Thất Sắc cầu vồng động) & bộ tự pha 2 kênh màu RGB tùy chỉnh
 *   (Kênh Chính = Trăng Non / Giáp Trắng, Kênh Phụ = Trăng Khuyết / Lõi Đen Song Nguyệt).
 * - Khung đặt hơi lệch trái để người chơi quan sát trực tiếp thanh kiếm ở góc nhìn Thứ 1 đổi màu theo thời gian thực!
 */
public final class TogetsuColorScreen extends Screen {
    private static final int PANEL_W = 304;
    private static final int PANEL_H = 252;

    // 12 ô màu chọn nhanh 1-click sắc nét
    private static final int[] QUICK_SWATCHES = {
            0x70D8FF, // Ice Cyan
            0x3B82FF, // Electric Blue
            0xC358FF, // Abyssal Violet
            0xFF5CE1, // Neon Magenta
            0xFF2B46, // Crimson Red
            0xFF7B29, // Flame Orange
            0xFFCC48, // Celestial Gold
            0x38FFB4, // Emerald Jade
            0x7CFF50, // Venom Lime
            0xEBF8FF, // Pure Moonlight White
            0xFF94C2, // Sakura Pink
            0x5E6CFF  // Deep Indigo
    };

    // Live preview state đọc trực tiếp bởi TogetsuGeoRenderer khi màn hình đang mở!
    private static boolean livePreviewActive = false;
    private static TogetsuGlowStyle liveStyle = TogetsuGlowStyle.YIN_YANG;
    private static int livePrimaryRgb = TogetsuGlowStyle.YIN_YANG.defaultPrimaryRgb;
    private static int liveSecondaryRgb = TogetsuGlowStyle.YIN_YANG.defaultSecondaryRgb;

    private TogetsuGlowStyle selectedStyle;
    private int primaryRgb;
    private int secondaryRgb;
    private boolean editingSecondary = false;

    private float hue;
    private float sat;
    private float val;
    private int draggingBar = -1; // 0 = Hue, 1 = Sat, 2 = Val

    private int panelLeft;
    private int panelTop;
    private int barX, barW, hueY, satY, valY, swatchY;

    private EditBox hexBox;
    private boolean updatingHexFromCode = false;
    private Button primaryTabBtn;
    private Button secondaryTabBtn;

    public TogetsuColorScreen(ItemStack stack) {
        super(Component.translatable("screen.abyssalrpg.togetsu_color.title"));
        this.selectedStyle = TogetsuItem.getGlowStyle(stack);
        this.primaryRgb = this.selectedStyle.resolvePrimaryRgb(TogetsuItem.getCustomPrimaryRgb(stack), 0.0F);
        this.secondaryRgb = this.selectedStyle.resolveSecondaryRgb(TogetsuItem.getCustomSecondaryRgb(stack), 0.0F);
        syncHsvFromActiveChannel();
        pushLivePreview();
    }

    public static boolean hasLivePreview() {
        return livePreviewActive;
    }

    public static TogetsuGlowStyle getLivePreviewStyle() {
        return liveStyle;
    }

    public static int getLivePreviewPrimaryRgb() {
        return livePrimaryRgb;
    }

    public static int getLivePreviewSecondaryRgb() {
        return liveSecondaryRgb;
    }

    private void pushLivePreview() {
        livePreviewActive = true;
        liveStyle = this.selectedStyle;
        livePrimaryRgb = this.primaryRgb;
        liveSecondaryRgb = this.secondaryRgb;
    }

    private static void clearLivePreview() {
        livePreviewActive = false;
    }

    @Override
    public boolean isPauseScreen() {
        // Không tạm dừng thế giới để thấy nhịp thở ánh sáng & cầu vồng sống động ngay trên thanh kiếm!
        return false;
    }

    @Override
    protected void init() {
        super.init();
        // Đặt khung hơi lệch trái khi màn hình đủ rộng để không che thanh kiếm ở tay phải góc nhìn thứ 1!
        int centerX = (this.width >= PANEL_W + 130) ? (this.width / 2 - 58) : (this.width / 2);
        this.panelLeft = Mth.clamp(centerX - PANEL_W / 2, 8, Math.max(8, this.width - PANEL_W - 8));
        this.panelTop = Mth.clamp((this.height - PANEL_H) / 2, 8, Math.max(8, this.height - PANEL_H - 8));

        // 6 Nút Preset (2 hàng × 3 cột)
        int gridX = this.panelLeft + 10;
        int gridY = this.panelTop + 26;
        int btnW = 92;
        int btnH = 20;
        int gapX = 4;
        int gapY = 4;
        for (int i = 0; i < TogetsuGlowStyle.PRESET_COUNT; i++) {
            final TogetsuGlowStyle st = TogetsuGlowStyle.byId(i);
            int col = i % 3;
            int row = i / 3;
            int bx = gridX + col * (btnW + gapX);
            int by = gridY + row * (btnH + gapY);
            this.addRenderableWidget(Button.builder(Component.translatable(st.translationKey()), b -> selectPreset(st))
                    .bounds(bx, by, btnW, btnH)
                    .build());
        }

        // Hàng tab chọn kênh màu tùy chỉnh: Màu Chính | Màu Phụ | Đồng Bộ 2 Bên
        int tabY = this.panelTop + 82;
        this.primaryTabBtn = this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.abyssalrpg.togetsu_color.tab_primary"),
                        b -> switchChannel(false))
                .bounds(gridX, tabY, 102, 18)
                .build());
        this.secondaryTabBtn = this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.abyssalrpg.togetsu_color.tab_secondary"),
                        b -> switchChannel(true))
                .bounds(gridX + 105, tabY, 102, 18)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.abyssalrpg.togetsu_color.sync_both"),
                        b -> syncBothChannels())
                .bounds(gridX + 210, tabY, 74, 18)
                .build());

        // Tọa độ 3 thanh gradient kéo trực tiếp (Hue / Saturation / Brightness) + 12 ô chọn nhanh
        this.barX = this.panelLeft + 52;
        this.barW = PANEL_W - 64;
        this.hueY = this.panelTop + 108;
        this.satY = this.panelTop + 126;
        this.valY = this.panelTop + 144;
        this.swatchY = this.panelTop + 165;

        // Ô nhập mã Hex #RRGGBB trực tiếp
        int hexY = this.panelTop + 188;
        this.hexBox = new EditBox(this.font, this.panelLeft + 52, hexY, 82, 18,
                Component.translatable("screen.abyssalrpg.togetsu_color.hex"));
        this.hexBox.setMaxLength(7);
        this.hexBox.setFilter(s -> s.matches("^#?[0-9a-fA-F]{0,6}$"));
        this.hexBox.setResponder(this::onHexTyped);
        this.addRenderableWidget(this.hexBox);
        refreshHexBox();

        // Nút Áp Dụng & Hủy ở hàng cuối
        int bottomY = this.panelTop + PANEL_H - 28;
        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.abyssalrpg.togetsu_color.apply"),
                        b -> applyAndClose())
                .bounds(this.panelLeft + 10, bottomY, 188, 20)
                .build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.abyssalrpg.togetsu_color.cancel"),
                        b -> this.onClose())
                .bounds(this.panelLeft + 204, bottomY, 90, 20)
                .build());

        refreshTabStates();
    }

    private void selectPreset(TogetsuGlowStyle st) {
        this.selectedStyle = st;
        this.primaryRgb = st.defaultPrimaryRgb;
        this.secondaryRgb = st.defaultSecondaryRgb;
        syncHsvFromActiveChannel();
        refreshHexBox();
        pushLivePreview();
    }

    private void switchChannel(boolean secondary) {
        this.editingSecondary = secondary;
        syncHsvFromActiveChannel();
        refreshHexBox();
        refreshTabStates();
    }

    private void syncBothChannels() {
        int active = this.editingSecondary ? this.secondaryRgb : this.primaryRgb;
        this.primaryRgb = active;
        this.secondaryRgb = active;
        this.selectedStyle = TogetsuGlowStyle.CUSTOM;
        refreshHexBox();
        pushLivePreview();
    }

    private void refreshTabStates() {
        if (this.primaryTabBtn != null) {
            this.primaryTabBtn.active = this.editingSecondary;
        }
        if (this.secondaryTabBtn != null) {
            this.secondaryTabBtn.active = !this.editingSecondary;
        }
    }

    private int activeChannelRgb() {
        return this.editingSecondary ? this.secondaryRgb : this.primaryRgb;
    }

    private void setActiveChannelCustomRgb(int rgb) {
        int clean = rgb & 0xFFFFFF;
        if (this.editingSecondary) {
            this.secondaryRgb = clean;
        } else {
            this.primaryRgb = clean;
        }
        this.selectedStyle = TogetsuGlowStyle.CUSTOM;
        pushLivePreview();
    }

    private void syncHsvFromActiveChannel() {
        float[] hsv = TogetsuGlowStyle.rgbToHsv(activeChannelRgb());
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = Math.max(0.25F, hsv[2]);
    }

    private void refreshHexBox() {
        if (this.hexBox == null) return;
        this.updatingHexFromCode = true;
        this.hexBox.setValue(String.format(Locale.ROOT, "#%06X", activeChannelRgb()));
        this.updatingHexFromCode = false;
    }

    private void onHexTyped(String text) {
        if (this.updatingHexFromCode) return;
        String clean = text.startsWith("#") ? text.substring(1) : text;
        if (clean.length() == 6) {
            try {
                int parsed = Integer.parseInt(clean, 16) & 0xFFFFFF;
                setActiveChannelCustomRgb(parsed);
                syncHsvFromActiveChannel();
            } catch (NumberFormatException ignored) {
                // Bỏ qua khi người chơi gõ dở
            }
        }
    }

    private void applyAndClose() {
        ClientPlayNetworking.send(new TogetsuColorC2SPayload(
                this.selectedStyle.id,
                this.primaryRgb & 0xFFFFFF,
                this.secondaryRgb & 0xFFFFFF,
                false
        ));
        clearLivePreview();
        Minecraft.getInstance().setScreen(null);
    }

    @Override
    public void onClose() {
        clearLivePreview();
        super.onClose();
    }

    @Override
    public void removed() {
        clearLivePreview();
        super.removed();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Phủ mờ siêu nhẹ (không làm nhòe màn hình) để thấy rõ thanh kiếm đổi màu realtime bên phải!
        g.fill(0, 0, this.width, this.height, 0x3A06080F);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        Minecraft mc = Minecraft.getInstance();
        float timeSec = (mc.level != null ? ((mc.level.getGameTime() % 240000L) + partialTick) : partialTick) / 20.0F;
        int curPri = this.selectedStyle.resolvePrimaryRgb(this.primaryRgb, timeSec);
        int curSec = this.selectedStyle.resolveSecondaryRgb(this.secondaryRgb, timeSec);

        // Khung bảng chính nền tối thanh lịch với viền ăn màu điểm sáng đang chọn!
        g.fill(this.panelLeft, this.panelTop, this.panelLeft + PANEL_W, this.panelTop + PANEL_H, 0xE80C0F18);
        g.renderOutline(this.panelLeft, this.panelTop, PANEL_W, PANEL_H, 0xFF000000 | curPri);
        g.fill(this.panelLeft + 1, this.panelTop + 1, this.panelLeft + PANEL_W - 1, this.panelTop + 21, 0xFF151B2B);

        // Tiêu đề & huy hiệu 2 màu xem trước
        g.drawString(this.font, this.title, this.panelLeft + 10, this.panelTop + 7, 0xFFF2F7FF, true);
        int badgeX = this.panelLeft + PANEL_W - 44;
        g.fill(badgeX, this.panelTop + 5, badgeX + 16, this.panelTop + 17, 0xFF000000 | curPri);
        g.renderOutline(badgeX, this.panelTop + 5, 16, 12, 0xCCFFFFFF);
        g.fill(badgeX + 20, this.panelTop + 5, badgeX + 36, this.panelTop + 17, 0xFF000000 | curSec);
        g.renderOutline(badgeX + 20, this.panelTop + 5, 16, 12, 0xCCFFFFFF);

        // Vẽ widget nút trước rồi vẽ vạch chấm màu preset + thanh gradient + 12 ô nhanh sắc nét
        super.render(g, mouseX, mouseY, partialTick);

        // Thanh chỉ thị 2 màu ngay dưới mép mỗi nút Preset + khung nổi bật nút đang chọn
        int gridX = this.panelLeft + 10;
        int gridY = this.panelTop + 26;
        for (int i = 0; i < TogetsuGlowStyle.PRESET_COUNT; i++) {
            TogetsuGlowStyle st = TogetsuGlowStyle.byId(i);
            int bx = gridX + (i % 3) * 96;
            int by = gridY + (i / 3) * 24;
            int sp = st.resolvePrimaryRgb(st.defaultPrimaryRgb, timeSec);
            int ss = st.resolveSecondaryRgb(st.defaultSecondaryRgb, timeSec);
            g.fill(bx + 2, by + 17, bx + 46, by + 19, 0xFF000000 | sp);
            g.fill(bx + 46, by + 17, bx + 90, by + 19, 0xFF000000 | ss);
            if (this.selectedStyle == st) {
                g.renderOutline(bx - 1, by - 1, 94, 22, 0xFFFFFFFF);
            }
        }

        // Tiêu đề mục Tự Pha Màu
        g.drawString(this.font, Component.translatable("screen.abyssalrpg.togetsu_color.custom_header"),
                this.panelLeft + 10, this.panelTop + 72, 0xFFAFC0D8, false);

        // 3 Thanh Gradient: Sắc Độ (Hue), Bão Hòa (Sat), Độ Sáng (Bright)
        g.drawString(this.font, "Hue", this.panelLeft + 10, this.hueY + 2, 0xFFD4DFEE, false);
        drawHueBar(g, this.barX, this.hueY, this.barW, 12);
        drawSliderKnob(g, this.barX, this.hueY, this.barW, 12, this.hue);

        g.drawString(this.font, "Sat", this.panelLeft + 10, this.satY + 2, 0xFFD4DFEE, false);
        drawSatBar(g, this.barX, this.satY, this.barW, 12);
        drawSliderKnob(g, this.barX, this.satY, this.barW, 12, this.sat);

        g.drawString(this.font, "Bright", this.panelLeft + 10, this.valY + 2, 0xFFD4DFEE, false);
        drawValBar(g, this.barX, this.valY, this.barW, 12);
        drawSliderKnob(g, this.barX, this.valY, this.barW, 12, (this.val - 0.25F) / 0.75F);

        // 12 Ô chọn màu nhanh 1-click
        g.drawString(this.font, "Quick", this.panelLeft + 10, this.swatchY + 3, 0xFFD4DFEE, false);
        int swStep = 20;
        for (int i = 0; i < QUICK_SWATCHES.length; i++) {
            int sx = this.barX + i * swStep;
            int col = QUICK_SWATCHES[i];
            g.fill(sx, this.swatchY, sx + 16, this.swatchY + 14, 0xFF000000 | col);
            boolean hov = mouseX >= sx && mouseX < sx + 16 && mouseY >= this.swatchY && mouseY < this.swatchY + 14;
            g.renderOutline(sx, this.swatchY, 16, 14, hov ? 0xFFFFFFFF : 0xAA3A465E);
        }

        // Nhãn Hex + ô xem trước 2 kênh hiện tại cạnh ô nhập Hex
        int hexY = this.panelTop + 188;
        g.drawString(this.font, "Hex", this.panelLeft + 10, hexY + 5, 0xFFD4DFEE, false);
        int previewX = this.panelLeft + 142;
        g.fill(previewX, hexY, previewX + 72, hexY + 18, 0xFF000000 | curPri);
        g.renderOutline(previewX, hexY, 72, 18, !this.editingSecondary ? 0xFFFFFFFF : 0xAA50607A);
        g.fill(previewX + 76, hexY, previewX + 148, hexY + 18, 0xFF000000 | curSec);
        g.renderOutline(previewX + 76, hexY, 72, 18, this.editingSecondary ? 0xFFFFFFFF : 0xAA50607A);

        // Gợi ý phím tắt nhanh dưới cùng
        g.drawString(this.font, Component.translatable("screen.abyssalrpg.togetsu_color.quick_hint"),
                this.panelLeft + 10, this.panelTop + PANEL_H - 38, 0xFF8C9BB5, false);
    }

    private void drawHueBar(GuiGraphics g, int x, int y, int w, int h) {
        int segs = 36;
        for (int i = 0; i < segs; i++) {
            int x0 = x + (i * w) / segs;
            int x1 = x + ((i + 1) * w) / segs;
            int rgb = TogetsuGlowStyle.hsvToRgb(i / (float) segs, 1.0F, 1.0F);
            g.fill(x0, y, x1, y + h, 0xFF000000 | rgb);
        }
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xAA4A5872);
    }

    private void drawSatBar(GuiGraphics g, int x, int y, int w, int h) {
        int segs = 24;
        for (int i = 0; i < segs; i++) {
            int x0 = x + (i * w) / segs;
            int x1 = x + ((i + 1) * w) / segs;
            int rgb = TogetsuGlowStyle.hsvToRgb(this.hue, i / (float) (segs - 1), this.val);
            g.fill(x0, y, x1, y + h, 0xFF000000 | rgb);
        }
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xAA4A5872);
    }

    private void drawValBar(GuiGraphics g, int x, int y, int w, int h) {
        int segs = 24;
        for (int i = 0; i < segs; i++) {
            int x0 = x + (i * w) / segs;
            int x1 = x + ((i + 1) * w) / segs;
            float v = Mth.lerp(i / (float) (segs - 1), 0.25F, 1.00F);
            int rgb = TogetsuGlowStyle.hsvToRgb(this.hue, this.sat, v);
            g.fill(x0, y, x1, y + h, 0xFF000000 | rgb);
        }
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xAA4A5872);
    }

    private void drawSliderKnob(GuiGraphics g, int x, int y, int w, int h, float frac) {
        int kx = x + Math.round(Mth.clamp(frac, 0.0F, 1.0F) * (w - 4));
        g.fill(kx, y - 2, kx + 4, y + h + 2, 0xFFFFFFFF);
        g.renderOutline(kx - 1, y - 2, 6, h + 4, 0xFF0B0E16);
    }

    private void updateBarFromMouse(int barIdx, double mouseX) {
        float frac = Mth.clamp((float) ((mouseX - this.barX) / (double) Math.max(1, this.barW)), 0.0F, 1.0F);
        if (barIdx == 0) {
            this.hue = frac;
        } else if (barIdx == 1) {
            this.sat = frac;
        } else if (barIdx == 2) {
            this.val = Mth.lerp(frac, 0.25F, 1.00F);
        }
        int rgb = TogetsuGlowStyle.hsvToRgb(this.hue, this.sat, this.val);
        setActiveChannelCustomRgb(rgb);
        refreshHexBox();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseX >= this.barX - 2 && mouseX <= this.barX + this.barW + 2) {
                if (mouseY >= this.hueY - 2 && mouseY <= this.hueY + 14) {
                    this.draggingBar = 0;
                    updateBarFromMouse(0, mouseX);
                    return true;
                }
                if (mouseY >= this.satY - 2 && mouseY <= this.satY + 14) {
                    this.draggingBar = 1;
                    updateBarFromMouse(1, mouseX);
                    return true;
                }
                if (mouseY >= this.valY - 2 && mouseY <= this.valY + 14) {
                    this.draggingBar = 2;
                    updateBarFromMouse(2, mouseX);
                    return true;
                }
            }
            // Click vào 12 ô màu nhanh
            if (mouseY >= this.swatchY && mouseY < this.swatchY + 14) {
                int swStep = 20;
                for (int i = 0; i < QUICK_SWATCHES.length; i++) {
                    int sx = this.barX + i * swStep;
                    if (mouseX >= sx && mouseX < sx + 16) {
                        setActiveChannelCustomRgb(QUICK_SWATCHES[i]);
                        syncHsvFromActiveChannel();
                        refreshHexBox();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.draggingBar >= 0) {
            updateBarFromMouse(this.draggingBar, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.draggingBar = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
