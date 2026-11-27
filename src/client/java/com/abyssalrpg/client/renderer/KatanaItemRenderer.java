package com.abyssalrpg.client.renderer;

import com.abyssalrpg.AbyssalRpgMod;
import com.abyssalrpg.client.KatanaClientState;
import com.abyssalrpg.client.anim.DualBladeAnimState;
import com.abyssalrpg.client.anim.KatanaAnimManager;
import com.abyssalrpg.combat.KatanaMoveType;
import com.abyssalrpg.item.KatanaItem;
import com.abyssalrpg.registry.ModItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Custom 3D Mesh ItemRenderer cho **Damascus Katana (`abyssalrpg:katana`)**:
 * - Chuỗi Combo 4 Đòn liền mạch (`HIT_1_DRAW`, `HIT_2_DOWN_DIAG`, `HIT_3_UP_DIAG`, `HIT_4_FINISHER`)
 *   + `CHIBURI_NOTO` + `DEFLECT` dùng **Quaternion Camera-Space Slerp chuẩn trục lưỡi sắc `+Z`**.
 * - Cắt mặt phẳng chính xác `emitMeshClippedY` giúp lưỡi hiện dần từ từ khỏi miệng vỏ ở Hit 1 & ẩn dần ở Chiburi/Noto.
 */
public final class KatanaItemRenderer {
    public static final ResourceLocation MESH_LOC = AbyssalRpgMod.id("models/katana_mesh.json");
    public static final ResourceLocation TEX_KATANA = AbyssalRpgMod.id("textures/item/katana_model.png");
    public static final ResourceLocation GLOW_KATANA = AbyssalRpgMod.id("textures/item/katana_glowmask.png");
    public static final ResourceLocation GUI_ICON_SHEATHED = AbyssalRpgMod.id("textures/item/katana.png");
    public static final ResourceLocation GUI_ICON_DRAWN = AbyssalRpgMod.id("textures/item/katana_drawn.png");

    // --- Exact Camera-Space Quaternions (100% Cyan=+Z sharp edge leading, verified!) ---
    private static final Quaternionf Q_SHEATH_IDLE = new Quaternionf(+0.94754F, -0.28275F, -0.11662F, +0.09286F);
    private static final Quaternionf Q_DRAWN_IDLE  = new Quaternionf(+0.00015F, +0.90920F, -0.30747F, -0.28074F);

    // Hit 1: Draw Slash Left -> Right slightly upward (0.40s / 8 ticks)
    private static final Quaternionf Q_H1_UNLOCK   = new Quaternionf(+0.29747F, -0.25843F, -0.89588F, -0.20522F);
    private static final Quaternionf Q_H1_CLEAR    = new Quaternionf(+0.65257F, -0.70178F, +0.15511F, -0.23999F);
    private static final Quaternionf Q_H1_MID      = new Quaternionf(+0.38858F, -0.59547F, +0.26280F, -0.65220F);
    private static final Quaternionf Q_H1_END      = new Quaternionf(+0.17951F, -0.20889F, +0.53629F, -0.79783F);

    // Hit 2: Downward Diagonal Top-Right -> Bottom-Left (0.35s / 7 ticks)
    private static final Quaternionf Q_H2_WIND     = new Quaternionf(+0.24939F, +0.91555F, -0.13508F, -0.28519F);
    private static final Quaternionf Q_H2_MID      = new Quaternionf(-0.00206F, +0.81411F, -0.42450F, -0.39626F);
    private static final Quaternionf Q_H2_END      = new Quaternionf(-0.07387F, +0.35836F, -0.79655F, -0.48128F);

    // Hit 3: Upward Diagonal Bottom-Left -> Top-Right (0.35s / 7 ticks, sharp edge +Z strictly UP-RIGHT!)
    private static final Quaternionf Q_H3_DIP      = new Quaternionf(-0.81876F, +0.48884F, +0.00207F, +0.30111F);
    private static final Quaternionf Q_H3_MID      = new Quaternionf(-0.52836F, +0.55857F, -0.02306F, +0.63899F);
    private static final Quaternionf Q_H3_END      = new Quaternionf(-0.15731F, +0.16071F, -0.30866F, +0.92420F);

    // Hit 4: Overhead Vertical Finisher Slam (0.55s / 11 ticks)
    private static final Quaternionf Q_H4_HIGH     = new Quaternionf(+0.03351F, +0.96435F, +0.26080F, -0.02976F);
    private static final Quaternionf Q_H4_MID      = new Quaternionf(-0.03535F, +0.88769F, -0.45729F, -0.04058F);
    private static final Quaternionf Q_H4_IMPACT   = new Quaternionf(-0.03856F, +0.46348F, -0.87749F, -0.11713F);

    // Chiburi & Sheathe (0.90s / 18 ticks, Roll 180° edge-UP Noto!)
    private static final Quaternionf Q_CHIB_TEMPLE = new Quaternionf(-0.01777F, +0.87456F, -0.31368F, +0.36936F);
    private static final Quaternionf Q_CHIB_FLICK  = new Quaternionf(+0.03581F, +0.27465F, -0.83028F, +0.48365F);
    private static final Quaternionf Q_NOTO_GUIDE  = new Quaternionf(+0.28673F, -0.42944F, -0.78607F, -0.33979F);
    private static final Quaternionf Q_NOTO_ALIGN  = new Quaternionf(+0.29747F, -0.25843F, -0.89588F, -0.20522F);

    // 5 Thế Đỡ Kenjutsu — Verified Camera-Space Quaternions (+Y = tip, +Z = sharp edge Ha, -Z = dull spine Mune!)
    private static final Quaternionf Q_GUARD_UKE   = new Quaternionf(+0.26585F, +0.81565F, -0.29666F, +0.41957F);
    private static final Quaternionf Q_GUARD_KASA  = new Quaternionf(+0.40204F, -0.40949F, +0.71341F, +0.40217F);
    private static final Quaternionf Q_SURIAGE_LOW = new Quaternionf(-0.16962F, -0.59498F, +0.42552F, +0.66042F);
    private static final Quaternionf Q_SURIAGE_UP  = new Quaternionf(-0.18060F, +0.08484F, -0.37005F, +0.90733F);
    private static final Quaternionf Q_GUARD_MUNE  = new Quaternionf(-0.07754F, +0.98050F, -0.11155F, +0.14196F);
    private static final Quaternionf Q_GUARD_SAYA  = new Quaternionf(-0.29855F, +0.79904F, -0.31750F, -0.41424F);

    // 5 Extended Branches — Verified Camera-Space Quaternions (+Y = tip, +Z = sharp edge!)
    private static final Quaternionf Q_THRUST_COIL = new Quaternionf(-0.49498F, -0.51476F, +0.48479F, +0.50497F);
    private static final Quaternionf Q_THRUST_EXT  = new Quaternionf(-0.48990F, -0.50000F, +0.50000F, +0.50990F);
    private static final Quaternionf Q_LOW_START   = new Quaternionf(+0.70711F, +0.66567F, -0.17366F, -0.16349F);
    private static final Quaternionf Q_LOW_END     = new Quaternionf(-0.17366F, -0.16349F, +0.70711F, +0.66567F);
    private static final Quaternionf Q_SPIN_HOLD   = new Quaternionf(+0.53878F, +0.53878F, -0.45795F, -0.45795F);
    private static final Quaternionf Q_IAIDO_BLINK = new Quaternionf(+0.78845F, +0.45594F, -0.41007F, -0.04805F);
    private static final Quaternionf Q_AIR1_START  = new Quaternionf(+0.88252F, -0.26685F, +0.03943F, -0.38523F);
    private static final Quaternionf Q_AIR1_PEAK   = new Quaternionf(+0.11974F, +0.96648F, -0.16112F, -0.16004F);
    private static final Quaternionf Q_AIR2_START  = new Quaternionf(+0.67584F, +0.68956F, -0.18219F, -0.18589F);
    private static final Quaternionf Q_AIR2_END    = new Quaternionf(-0.18219F, -0.18589F, +0.67584F, +0.68956F);
    private static final Quaternionf Q_AIR3_DIVE   = new Quaternionf(+0.00000F, +0.98498F, +0.17266F, +0.00000F);
    private static final Quaternionf Q_AIR3_IMPACT = new Quaternionf(+0.00000F, -0.14142F, +0.98995F, -0.00000F);
    private static final Quaternionf Q_COUNTER_END = new Quaternionf(-0.19501F, +0.13817F, -0.44947F, +0.86073F);

    private static float[] swordData;
    private static float[] scabbardData;
    private static boolean meshLoaded = false;
    private static int currentRenderEntityId = -1;

    public static void setCurrentRenderEntityId(int entityId) {
        currentRenderEntityId = entityId;
    }

    private static void ensureMeshLoaded() {
        if (meshLoaded) {
            return;
        }
        meshLoaded = true;
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(MESH_LOC);
            if (res.isEmpty()) {
                AbyssalRpgMod.LOGGER.error("Missing Katana 3D mesh resource: {}", MESH_LOC);
                return;
            }
            try (InputStreamReader reader = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                swordData = parseQuadArray(root.getAsJsonArray("sword"));
                scabbardData = parseQuadArray(root.getAsJsonArray("scabbard"));
            }
        } catch (Exception e) {
            AbyssalRpgMod.LOGGER.error("Failed to load Katana 3D mesh JSON: {}", MESH_LOC, e);
        }
    }

    private static float[] parseQuadArray(JsonArray arr) {
        if (arr == null || arr.isEmpty()) {
            return new float[0];
        }
        float[] out = new float[arr.size() * 32];
        int ptr = 0;
        for (JsonElement el : arr) {
            JsonObject q = el.getAsJsonObject();
            JsonArray n = q.getAsJsonArray("n");
            float nx = n.get(0).getAsFloat();
            float ny = n.get(1).getAsFloat();
            float nz = n.get(2).getAsFloat();
            JsonArray verts = q.getAsJsonArray("v");
            for (int k = 0; k < 4; k++) {
                JsonArray v = verts.get(k).getAsJsonArray();
                out[ptr++] = v.get(0).getAsFloat();
                out[ptr++] = v.get(1).getAsFloat();
                out[ptr++] = v.get(2).getAsFloat();
                out[ptr++] = v.get(3).getAsFloat();
                out[ptr++] = v.get(4).getAsFloat();
                out[ptr++] = nx;
                out[ptr++] = ny;
                out[ptr++] = nz;
            }
        }
        return out;
    }

    public void renderByItem(ItemStack stack, ItemDisplayContext transformType, PoseStack poseStack,
                             MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (transformType == ItemDisplayContext.GUI) {
            renderCrispGuiIcon(stack, poseStack, bufferSource);
            return;
        }
        ensureMeshLoaded();

        boolean isFirstPerson = (transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND);
        boolean isLeftHand = (transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND);

        int entityId = currentRenderEntityId;
        if (entityId == -1) {
            var lp = Minecraft.getInstance().player;
            if (lp != null && (isFirstPerson || lp.getMainHandItem().is(ModItems.KATANA))) {
                entityId = lp.getId();
            }
        }

        if (transformType == ItemDisplayContext.GROUND
                || transformType == ItemDisplayContext.FIXED
                || transformType == ItemDisplayContext.HEAD
                || transformType == ItemDisplayContext.NONE) {
            poseStack.pushPose();
            poseStack.translate(0.5F, 0.42F, 0.5F);
            poseStack.scale(0.95F, 0.95F, 0.95F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-16.0F));
            renderPart(swordData, poseStack, bufferSource, packedLight, packedOverlay, 0.78F);
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, -0.09F);
            renderPart(scabbardData, poseStack, bufferSource, packedLight, packedOverlay, 0.72F);
            poseStack.popPose();
            poseStack.popPose();
            return;
        }

        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        float glowBoost = (entityId != -1) ? KatanaAnimManager.getGlowBoost(entityId, partialTick) : 0.0F;
        float glowIntensity = Mth.clamp(0.76F + glowBoost * 0.24F, 0.0F, 1.0F);
        float drawBlend = (entityId != -1)
                ? KatanaAnimManager.getDrawBlend(entityId, partialTick)
                : (KatanaItem.isDrawn(stack) ? 1.0F : 0.0F);

        int blk = Math.max(LightTexture.block(packedLight), glowBoost > 0.2F ? 14 : 11);
        int sky = Math.max(LightTexture.sky(packedLight), 13);
        int effectiveLight = LightTexture.pack(blk, sky);

        if (isFirstPerson) {
            renderFirstPersonKatana(entityId, isLeftHand, partialTick, drawBlend, glowIntensity,
                    poseStack, bufferSource, effectiveLight, packedLight, packedOverlay);
            return;
        }

        float revealFrac = (!isLeftHand && entityId != -1)
                ? KatanaAnimManager.getBladeRevealFraction(entityId, partialTick)
                : 1.0F;
        boolean gradualDraw = revealFrac < 0.995F;
        float revealMaxY = gradualDraw ? (0.205F + revealFrac * 1.44F) : 10.0F;
        boolean showHandScabbard = !isLeftHand && (gradualDraw || drawBlend < 0.72F);

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);
        renderPartClippedY(swordData, poseStack, bufferSource, effectiveLight, packedOverlay, glowIntensity, revealMaxY);
        if (showHandScabbard) {
            poseStack.pushPose();
            float scabOffset = gradualDraw ? (revealFrac * 1.44F) : (drawBlend * 1.45F);
            poseStack.translate(0.0F, +scabOffset, 0.0F);
            renderPart(scabbardData, poseStack, bufferSource, effectiveLight, packedOverlay, 0.74F);
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void renderFirstPersonKatana(int entityId, boolean isLeftHand, float partialTick,
                                                float drawBlend, float glowIntensity,
                                                PoseStack poseStack, MultiBufferSource bufferSource,
                                                int effectiveLight, int armLight, int packedOverlay) {
        float alpha = (entityId != -1) ? KatanaAnimManager.getActionProgress(entityId, partialTick) : 0.0F;
        KatanaMoveType move = (entityId != -1) ? KatanaAnimManager.getMoveType(entityId) : KatanaMoveType.HIT_1_DRAW;
        float guard = (entityId != -1) ? KatanaAnimManager.getGuardBlend(entityId, partialTick) : 0.0F;
        float sprint = (entityId != -1) ? KatanaAnimManager.getSprintBlend(entityId, partialTick) : 0.0F;

        // 1) HIT 1: Rút kiếm chém lướt ngang hơi chếch lên Trái -> Phải (`HIT_1_DRAW`, 0.40s / 8 ticks)
        if (alpha > 0.001F && move == KatanaMoveType.HIT_1_DRAW && !isLeftHand) {
            boolean fromSheathed = (entityId != -1) && KatanaAnimManager.isHit1FromSheathed(entityId);
            float ihx, ihy, ihz, isc = 0.96F;
            Quaternionf swordQ = new Quaternionf();
            float scabDrop = fromSheathed ? 0.0F : 1.0F;

            if (alpha < 0.30F) {
                float u = DualBladeAnimState.easeOutCubic(alpha / 0.30F);
                ihx = Mth.lerp(u, fromSheathed ? -0.52F : 0.56F, -0.46F);
                ihy = Mth.lerp(u, fromSheathed ? -0.28F : -0.66F, -0.26F);
                ihz = Mth.lerp(u, fromSheathed ? -0.66F : -0.82F, -0.68F);
                slerpShortest(fromSheathed ? Q_SHEATH_IDLE : Q_DRAWN_IDLE, Q_H1_UNLOCK, u, swordQ);
            } else if (alpha < 0.45F) {
                float u = easeOutExpo((alpha - 0.30F) / 0.15F);
                ihx = Mth.lerp(u, -0.46F, -0.14F);
                ihy = Mth.lerp(u, -0.26F, -0.14F);
                ihz = Mth.lerp(u, -0.68F, -0.78F);
                slerpShortest(Q_H1_UNLOCK, Q_H1_CLEAR, u, swordQ);
                scabDrop = fromSheathed ? Mth.lerp(u, 0.0F, 0.85F) : 1.0F;
            } else if (alpha < 0.60F) {
                float s = easeOutExpo((alpha - 0.45F) / 0.15F);
                if (s < 0.5F) {
                    float u = s * 2.0F;
                    ihx = Mth.lerp(u, -0.14F, +0.08F);
                    ihy = Mth.lerp(u, -0.14F, -0.32F);
                    ihz = Mth.lerp(u, -0.78F, -0.84F);
                    slerpShortest(Q_H1_CLEAR, Q_H1_MID, u, swordQ);
                } else {
                    float u = (s - 0.5F) * 2.0F;
                    ihx = Mth.lerp(u, +0.08F, +0.46F);
                    ihy = Mth.lerp(u, -0.32F, -0.22F);
                    ihz = Mth.lerp(u, -0.84F, -0.80F);
                    slerpShortest(Q_H1_MID, Q_H1_END, u, swordQ);
                }
                scabDrop = 1.0F;
            } else if (alpha < 0.85F) {
                // Giữ đà bên phải ngang vai chờ nối thẳng Hit 2 tại mốc 0.25s (Tick 5)!
                ihx = +0.46F;
                ihy = -0.22F;
                ihz = -0.80F;
                swordQ.set(Q_H1_END);
                scabDrop = 1.0F;
            } else {
                float u = DualBladeAnimState.easeInOutCubic((alpha - 0.85F) / 0.15F);
                ihx = Mth.lerp(u, +0.46F, 0.56F);
                ihy = Mth.lerp(u, -0.22F, -0.66F);
                ihz = Mth.lerp(u, -0.80F, -0.82F);
                isc = Mth.lerp(u, 0.96F, 1.00F);
                slerpShortest(Q_H1_END, Q_DRAWN_IDLE, u, swordQ);
                scabDrop = 1.0F;
            }

            poseStack.pushPose();
            poseStack.translate(0.5F - 0.56F, 0.5F + 0.52F, 0.5F + 0.72F);

            if (fromSheathed && scabDrop < 0.85F) {
                float sx = (alpha < 0.30F) ? ihx : (-0.46F - scabDrop * 0.36F);
                float sy = (alpha < 0.30F) ? ihy : (-0.26F - scabDrop * 0.42F);
                float sz = (alpha < 0.30F) ? ihz : -0.68F;
                Quaternionf scabQ = (alpha < 0.30F) ? swordQ : Q_H1_UNLOCK;
                poseStack.pushPose();
                poseStack.translate(sx, sy, sz);
                poseStack.mulPose(scabQ);
                poseStack.scale(0.96F, 0.96F, 0.96F);
                renderPart(scabbardData, poseStack, bufferSource, effectiveLight, packedOverlay, 0.72F);
                poseStack.popPose();
                // Tay trái nắm thân vỏ Saya khi rút kiếm Iaido!
                renderFirstPersonPlayerArm(poseStack, bufferSource, entityId, true,
                        sx, sy, sz, 0.96F, +0.40F, scabQ, armLight, Mth.clamp(scabDrop * 1.15F, 0.0F, 1.0F));
            }

            float revealFrac = (entityId != -1) ? KatanaAnimManager.getBladeRevealFraction(entityId, partialTick) : 1.0F;
            float revealMaxY = (revealFrac >= 0.995F) ? 10.0F : (0.205F + revealFrac * 1.44F);
            poseStack.pushPose();
            poseStack.translate(ihx, ihy, ihz);
            poseStack.mulPose(swordQ);
            poseStack.scale(isc, isc, isc);
            renderPartClippedY(swordData, poseStack, bufferSource, effectiveLight, packedOverlay, 1.0F, revealMaxY);
            poseStack.popPose();

            // Tay phải mang đúng skin người chơi nắm chuôi tsuka vung kiếm rút vỏ!
            float retract = (alpha > 0.88F) ? ((alpha - 0.88F) / 0.12F) : 0.0F;
            renderFirstPersonPlayerArm(poseStack, bufferSource, entityId, false,
                    ihx, ihy, ihz, isc, +0.06F, swordQ, armLight, retract);
            poseStack.popPose();
            return;
        }

        // 2) VẨY MÁU & TRA KIẾM VÀO VỎ (`CHIBURI_NOTO`, 0.90s / 18 ticks)
        if (alpha > 0.001F && move == KatanaMoveType.CHIBURI_NOTO && !isLeftHand) {
            float chx, chy, chz, csc = 0.96F;
            Quaternionf swordQ = new Quaternionf();
            float scabVis;

            if (alpha < 0.089F) {
                float u = DualBladeAnimState.easeOutCubic(alpha / 0.089F);
                chx = Mth.lerp(u, 0.56F, 0.44F);
                chy = Mth.lerp(u, -0.66F, -0.06F);
                chz = Mth.lerp(u, -0.82F, -0.80F);
                slerpShortest(Q_DRAWN_IDLE, Q_CHIB_TEMPLE, u, swordQ);
                scabVis = 0.0F;
            } else if (alpha < 0.167F) {
                float flick = easeOutExpo((alpha - 0.089F) / 0.078F);
                chx = Mth.lerp(flick, 0.44F, 0.34F);
                chy = Mth.lerp(flick, -0.06F, -0.30F);
                chz = -0.80F;
                slerpShortest(Q_CHIB_TEMPLE, Q_CHIB_FLICK, flick, swordQ);
                scabVis = 0.0F;
            } else if (alpha < 0.278F) {
                chx = 0.34F;
                chy = -0.30F;
                chz = -0.80F;
                swordQ.set(Q_CHIB_FLICK);
                scabVis = 0.0F;
            } else if (alpha < 0.611F) {
                float u = DualBladeAnimState.easeInOutCubic((alpha - 0.278F) / 0.333F);
                chx = Mth.lerp(u, 0.34F, +0.24F);
                chy = Mth.lerp(u, -0.30F, -0.14F);
                chz = Mth.lerp(u, -0.80F, -0.76F);
                slerpShortest(Q_CHIB_FLICK, Q_NOTO_GUIDE, u, swordQ);
                scabVis = DualBladeAnimState.easeOutCubic(Mth.clamp((alpha - 0.34F) / 0.20F, 0.0F, 1.0F));
            } else if (alpha < 0.889F) {
                float push = DualBladeAnimState.easeInOutCubic((alpha - 0.611F) / 0.278F);
                chx = Mth.lerp(push, +0.24F, -0.43F);
                chy = Mth.lerp(push, -0.14F, -0.23F);
                chz = Mth.lerp(push, -0.76F, -0.68F);
                slerpShortest(Q_NOTO_GUIDE, Q_NOTO_ALIGN, push, swordQ);
                scabVis = 1.0F;
            } else {
                float lock = DualBladeAnimState.easeInOutCubic((alpha - 0.889F) / 0.111F);
                chx = Mth.lerp(lock, -0.43F, -0.52F);
                chy = Mth.lerp(lock, -0.23F, -0.28F);
                chz = Mth.lerp(lock, -0.68F, -0.66F);
                slerpShortest(Q_NOTO_ALIGN, Q_SHEATH_IDLE, lock, swordQ);
                scabVis = 1.0F;
            }

            poseStack.pushPose();
            poseStack.translate(0.5F - 0.56F, 0.5F + 0.52F, 0.5F + 0.72F);

            if (scabVis > 0.02F) {
                float sDrop = (1.0F - scabVis) * 0.42F;
                float sx = (alpha >= 0.889F) ? chx : (-0.46F - sDrop * 0.5F);
                float sy = (alpha >= 0.889F) ? chy : (-0.26F - sDrop);
                float sz = (alpha >= 0.889F) ? chz : -0.68F;
                Quaternionf scabQ = (alpha >= 0.889F) ? swordQ : Q_NOTO_ALIGN;
                poseStack.pushPose();
                poseStack.translate(sx, sy, sz);
                poseStack.mulPose(scabQ);
                poseStack.scale(0.96F, 0.96F, 0.96F);
                renderPart(scabbardData, poseStack, bufferSource, effectiveLight, packedOverlay, 0.72F);
                poseStack.popPose();
                float lRetract = Math.max(1.0F - scabVis, (alpha > 0.90F) ? ((alpha - 0.90F) / 0.10F) : 0.0F);
                renderFirstPersonPlayerArm(poseStack, bufferSource, entityId, true,
                        sx, sy, sz, 0.96F, +0.40F, scabQ, armLight, lRetract);
            }

            float revealFrac = (entityId != -1) ? KatanaAnimManager.getBladeRevealFraction(entityId, partialTick) : 1.0F;
            float revealMaxY = (revealFrac >= 0.995F) ? 10.0F : (0.205F + revealFrac * 1.44F);
            poseStack.pushPose();
            poseStack.translate(chx, chy, chz);
            poseStack.mulPose(swordQ);
            poseStack.scale(csc, csc, csc);
            renderPartClippedY(swordData, poseStack, bufferSource, effectiveLight, packedOverlay, glowIntensity, revealMaxY);
            poseStack.popPose();
            float rRetract = (alpha > 0.90F) ? ((alpha - 0.90F) / 0.10F) : 0.0F;
            renderFirstPersonPlayerArm(poseStack, bufferSource, entityId, false,
                    chx, chy, chz, csc, +0.06F, swordQ, armLight, rRetract);
            poseStack.popPose();
            return;
        }

        // 2B) NHÁNH 3: NHẤT KIẾM TẤT SÁT (`IAIDO_BLINK`, 0.70s sau thả nút -> dáng duỗi kiếm chéo phải-dưới -> Noto tra kiếm "tách"!)
        if (alpha > 0.001F && move == KatanaMoveType.IAIDO_BLINK && !isLeftHand) {
            float chx, chy, chz, csc = 0.96F;
            Quaternionf swordQ = new Quaternionf();
            float scabVis;

            if (alpha < 0.24F) {
                chx = 0.44F; chy = -0.34F; chz = -0.78F;
                swordQ.set(Q_IAIDO_BLINK);
                scabVis = 0.0F;
            } else if (alpha < 0.52F) {
                float u = DualBladeAnimState.easeInOutCubic((alpha - 0.24F) / 0.28F);
                chx = Mth.lerp(u, 0.44F, +0.24F);
                chy = Mth.lerp(u, -0.34F, -0.14F);
                chz = Mth.lerp(u, -0.78F, -0.76F);
                slerpShortest(Q_IAIDO_BLINK, Q_NOTO_GUIDE, u, swordQ);
                scabVis = DualBladeAnimState.easeOutCubic(Mth.clamp((alpha - 0.30F) / 0.20F, 0.0F, 1.0F));
            } else if (alpha < 0.90F) {
                float push = DualBladeAnimState.easeInOutCubic((alpha - 0.52F) / 0.38F);
                chx = Mth.lerp(push, +0.24F, -0.43F);
                chy = Mth.lerp(push, -0.14F, -0.23F);
                chz = Mth.lerp(push, -0.76F, -0.68F);
                slerpShortest(Q_NOTO_GUIDE, Q_NOTO_ALIGN, push, swordQ);
                scabVis = 1.0F;
            } else {
                float lock = DualBladeAnimState.easeInOutCubic((alpha - 0.90F) / 0.10F);
                chx = Mth.lerp(lock, -0.43F, -0.52F);
                chy = Mth.lerp(lock, -0.23F, -0.28F);
                chz = Mth.lerp(lock, -0.68F, -0.66F);
                slerpShortest(Q_NOTO_ALIGN, Q_SHEATH_IDLE, lock, swordQ);
                scabVis = 1.0F;
            }

            poseStack.pushPose();
            poseStack.translate(0.5F - 0.56F, 0.5F + 0.52F, 0.5F + 0.72F);
            if (scabVis > 0.02F) {
                float sDrop = (1.0F - scabVis) * 0.42F;
                float sx = (alpha >= 0.90F) ? chx : (-0.46F - sDrop * 0.5F);
                float sy = (alpha >= 0.90F) ? chy : (-0.26F - sDrop);
                float sz = (alpha >= 0.90F) ? chz : -0.68F;
                Quaternionf scabQ = (alpha >= 0.90F) ? swordQ : Q_NOTO_ALIGN;
                poseStack.pushPose();
                poseStack.translate(sx, sy, sz);
                poseStack.mulPose(scabQ);
                poseStack.scale(0.96F, 0.96F, 0.96F);
                renderPart(scabbardData, poseStack, bufferSource, effectiveLight, packedOverlay, 0.72F);
                poseStack.popPose();
                float lRetract = Math.max(1.0F - scabVis, (alpha > 0.90F) ? ((alpha - 0.90F) / 0.10F) : 0.0F);
                renderFirstPersonPlayerArm(poseStack, bufferSource, entityId, true,
                        sx, sy, sz, 0.96F, +0.40F, scabQ, armLight, lRetract);
            }
            float revealFrac = (entityId != -1) ? KatanaAnimManager.getBladeRevealFraction(entityId, partialTick) : 1.0F;
            float revealMaxY = (revealFrac >= 0.995F) ? 10.0F : (0.205F + revealFrac * 1.44F);
            poseStack.pushPose();
            poseStack.translate(chx, chy, chz);
            poseStack.mulPose(swordQ);
            poseStack.scale(csc, csc, csc);
            renderPartClippedY(swordData, poseStack, bufferSource, effectiveLight, packedOverlay, 1.0F, revealMaxY);
            poseStack.popPose();
            float rRetract = (alpha > 0.90F) ? ((alpha - 0.90F) / 0.10F) : 0.0F;
            renderFirstPersonPlayerArm(poseStack, bufferSource, entityId, false,
                    chx, chy, chz, csc, +0.06F, swordQ, armLight, rRetract);
            poseStack.popPose();
            return;
        }

        // 3) HIT 2, HIT 3, HIT 4, DEFLECT & CÁC NHÁNH MỞ RỘNG (THRUST, DELAY_SWEEP_360, AIR 1-3, PARRY_COUNTER)!
        if (alpha > 0.001F && !isLeftHand) {
            float ax = 0.56F, ay = -0.66F, az = -0.82F, asc = 0.96F;
            boolean renderSheathedSaya = false;
            Quaternionf swordQ = new Quaternionf();

            switch (move) {
                case HIT_2_DOWN_DIAG -> {
                    if (alpha < 0.286F) {
                        float u = DualBladeAnimState.easeOutCubic(alpha / 0.286F);
                        ax = Mth.lerp(u, +0.46F, +0.50F);
                        ay = Mth.lerp(u, -0.22F, -0.06F);
                        az = Mth.lerp(u, -0.80F, -0.80F);
                        slerpShortest(Q_H1_END, Q_H2_WIND, u, swordQ);
                    } else if (alpha < 0.571F) {
                        float s = easeOutExpo((alpha - 0.286F) / 0.285F);
                        if (s < 0.5F) {
                            float u = s * 2.0F;
                            ax = Mth.lerp(u, +0.50F, +0.22F);
                            ay = Mth.lerp(u, -0.06F, -0.30F);
                            az = Mth.lerp(u, -0.80F, -0.84F);
                            slerpShortest(Q_H2_WIND, Q_H2_MID, u, swordQ);
                        } else {
                            float u = (s - 0.5F) * 2.0F;
                            ax = Mth.lerp(u, +0.22F, -0.24F);
                            ay = Mth.lerp(u, -0.30F, -0.38F);
                            az = Mth.lerp(u, -0.84F, -0.82F);
                            slerpShortest(Q_H2_MID, Q_H2_END, u, swordQ);
                        }
                    } else if (alpha < 0.829F) {
                        float b = (alpha - 0.571F) / 0.258F;
                        float bounce = (b < 0.65F) ? (Mth.sin(b * 22.0F) * (float) Math.exp(-b * 6.5F)) : 0.0F;
                        ax = -0.24F;
                        ay = -0.38F + bounce * 0.025F;
                        az = -0.82F;
                        swordQ.set(Q_H2_END).rotateLocalX(bounce * 4.5F * Mth.DEG_TO_RAD);
                    } else {
                        float u = DualBladeAnimState.easeInOutCubic((alpha - 0.829F) / 0.171F);
                        ax = Mth.lerp(u, -0.24F, 0.56F);
                        ay = Mth.lerp(u, -0.38F, -0.66F);
                        az = Mth.lerp(u, -0.82F, -0.82F);
                        asc = Mth.lerp(u, 0.96F, 1.00F);
                        slerpShortest(Q_H2_END, Q_DRAWN_IDLE, u, swordQ);
                    }
                }
                case HIT_3_UP_DIAG -> {
                    if (alpha < 0.229F) {
                        float u = DualBladeAnimState.easeOutCubic(alpha / 0.229F);
                        ax = Mth.lerp(u, -0.24F, -0.26F);
                        ay = Mth.lerp(u, -0.38F, -0.42F);
                        az = -0.82F;
                        slerpShortest(Q_H2_END, Q_H3_DIP, u, swordQ);
                    } else if (alpha < 0.514F) {
                        float s = easeOutExpo((alpha - 0.229F) / 0.285F);
                        if (s < 0.5F) {
                            float u = s * 2.0F;
                            ax = Mth.lerp(u, -0.26F, +0.16F);
                            ay = Mth.lerp(u, -0.42F, -0.34F);
                            az = Mth.lerp(u, -0.82F, -0.84F);
                            slerpShortest(Q_H3_DIP, Q_H3_MID, u, swordQ);
                        } else {
                            float u = (s - 0.5F) * 2.0F;
                            ax = Mth.lerp(u, +0.16F, +0.46F);
                            ay = Mth.lerp(u, -0.34F, -0.08F);
                            az = Mth.lerp(u, -0.84F, -0.80F);
                            slerpShortest(Q_H3_MID, Q_H3_END, u, swordQ);
                        }
                    } else if (alpha < 0.800F) {
                        ax = +0.46F; ay = -0.08F; az = -0.80F;
                        swordQ.set(Q_H3_END);
                    } else {
                        float u = DualBladeAnimState.easeInOutCubic((alpha - 0.800F) / 0.200F);
                        ax = Mth.lerp(u, +0.46F, 0.56F);
                        ay = Mth.lerp(u, -0.08F, -0.66F);
                        az = Mth.lerp(u, -0.80F, -0.82F);
                        asc = Mth.lerp(u, 0.96F, 1.00F);
                        slerpShortest(Q_H3_END, Q_DRAWN_IDLE, u, swordQ);
                    }
                }
                case HIT_4_FINISHER -> {
                    if (alpha < 0.327F) {
                        float u = DualBladeAnimState.easeInOutCubic(alpha / 0.327F);
                        ax = Mth.lerp(u, +0.46F, +0.18F);
                        ay = Mth.lerp(u, -0.08F, -0.08F);
                        az = Mth.lerp(u, -0.80F, -0.80F);
                        slerpShortest(Q_H3_END, Q_H4_HIGH, u, swordQ);
                    } else if (alpha < 0.545F) {
                        float s = DualBladeAnimState.easeOutQuart((alpha - 0.327F) / 0.218F);
                        if (s < 0.48F) {
                            float u = s / 0.48F;
                            ax = Mth.lerp(u, +0.18F, +0.16F);
                            ay = Mth.lerp(u, -0.08F, -0.30F);
                            az = Mth.lerp(u, -0.80F, -0.84F);
                            slerpShortest(Q_H4_HIGH, Q_H4_MID, u, swordQ);
                        } else {
                            float u = (s - 0.48F) / 0.52F;
                            ax = Mth.lerp(u, +0.16F, +0.12F);
                            ay = Mth.lerp(u, -0.30F, -0.40F);
                            az = Mth.lerp(u, -0.84F, -0.86F);
                            slerpShortest(Q_H4_MID, Q_H4_IMPACT, u, swordQ);
                        }
                    } else if (alpha < 0.727F) {
                        float v = (alpha - 0.545F) / 0.182F;
                        float vib = Mth.sin(v * Mth.TWO_PI * 2.5F) * (1.0F - v * 0.45F);
                        ax = +0.12F; ay = -0.40F + vib * 0.014F; az = -0.86F;
                        swordQ.set(Q_H4_IMPACT).rotateLocalX(vib * 2.4F * Mth.DEG_TO_RAD);
                    } else {
                        float u = DualBladeAnimState.easeInOutCubic((alpha - 0.727F) / 0.273F);
                        ax = Mth.lerp(u, +0.12F, 0.56F);
                        ay = Mth.lerp(u, -0.40F, -0.66F);
                        az = Mth.lerp(u, -0.86F, -0.82F);
                        asc = Mth.lerp(u, 0.96F, 1.00F);
                        slerpShortest(Q_H4_IMPACT, Q_DRAWN_IDLE, u, swordQ);
                    }
                }
                case THRUST -> {
                    // Nhánh 1: Thu sát hông phải -> Đâm thẳng một tay Katate-tsuki hết tầm với -> Hitstop 0.08s -> Rút về!
                    if (alpha < 0.36F) {
                        float u = DualBladeAnimState.easeOutCubic(alpha / 0.36F);
                        ax = Mth.lerp(u, +0.44F, +0.34F);
                        ay = Mth.lerp(u, -0.44F, -0.34F);
                        az = Mth.lerp(u, -0.78F, -0.48F);
                        slerpShortest(Q_DRAWN_IDLE, Q_THRUST_COIL, u, swordQ);
                    } else if (alpha < 0.60F) {
                        float u = easeOutExpo((alpha - 0.36F) / 0.24F);
                        ax = Mth.lerp(u, +0.34F, +0.14F);
                        ay = Mth.lerp(u, -0.34F, -0.22F);
                        az = Mth.lerp(u, -0.48F, -1.26F);
                        slerpShortest(Q_THRUST_COIL, Q_THRUST_EXT, u, swordQ);
                    } else if (alpha < 0.76F) {
                        ax = +0.14F; ay = -0.22F; az = -1.26F;
                        swordQ.set(Q_THRUST_EXT);
                    } else {
                        float u = DualBladeAnimState.easeInOutCubic((alpha - 0.76F) / 0.24F);
                        ax = Mth.lerp(u, +0.14F, 0.56F);
                        ay = Mth.lerp(u, -0.22F, -0.66F);
                        az = Mth.lerp(u, -1.26F, -0.82F);
                        asc = Mth.lerp(u, 0.96F, 1.00F);
                        slerpShortest(Q_THRUST_EXT, Q_DRAWN_IDLE, u, swordQ);
                    }
                }
                case DELAY_SWEEP_360 -> {
                    // Nhánh 2: Quét ngang thấp Phải->Trái (0..0.455) -> Bật lên duỗi ngang xoay 360° (0.455..0.90) -> Thu!
                    if (alpha < 0.14F) {
                        float u = DualBladeAnimState.easeOutQuart(alpha / 0.14F);
                        ax = Mth.lerp(u, +0.46F, +0.48F);
                        ay = Mth.lerp(u, -0.22F, -0.44F);
                        az = -0.80F;
                        slerpShortest(Q_H1_END, Q_LOW_START, u, swordQ);
                    } else if (alpha < 0.455F) {
                        float u = easeOutExpo((alpha - 0.14F) / 0.315F);
                        ax = Mth.lerp(u, +0.48F, -0.42F);
                        ay = -0.44F;
                        az = Mth.lerp(u, -0.80F, -0.86F);
                        slerpShortest(Q_LOW_START, Q_LOW_END, u, swordQ);
                    } else if (alpha < 0.90F) {
                        float s = (alpha - 0.455F) / 0.445F;
                        ax = Mth.lerp(s, -0.42F, +0.44F);
                        ay = Mth.lerp(s, -0.44F, -0.24F);
                        az = -0.88F;
                        slerpShortest(Q_LOW_END, Q_SPIN_HOLD, Mth.clamp(s * 2.2F, 0.0F, 1.0F), swordQ);
                    } else {
                        float u = DualBladeAnimState.easeInOutCubic((alpha - 0.90F) / 0.10F);
                        ax = Mth.lerp(u, +0.44F, 0.56F);
                        ay = Mth.lerp(u, -0.24F, -0.66F);
                        az = Mth.lerp(u, -0.88F, -0.82F);
                        asc = Mth.lerp(u, 0.96F, 1.00F);
                        slerpShortest(Q_SPIN_HOLD, Q_DRAWN_IDLE, u, swordQ);
                    }
                }
                case AIR_1_LAUNCH -> {
                    if (alpha < 0.22F) {
                        float u = DualBladeAnimState.easeOutQuart(alpha / 0.22F);
                        ax = Mth.lerp(u, 0.40F, -0.24F);
                        ay = Mth.lerp(u, -0.50F, -0.46F);
                        az = -0.80F;
                        slerpShortest(Q_DRAWN_IDLE, Q_AIR1_START, u, swordQ);
                    } else if (alpha < 0.74F) {
                        float u = easeOutExpo((alpha - 0.22F) / 0.52F);
                        ax = Mth.lerp(u, -0.24F, +0.26F);
                        ay = Mth.lerp(u, -0.46F, +0.06F);
                        az = -0.82F;
                        slerpShortest(Q_AIR1_START, Q_AIR1_PEAK, u, swordQ);
                    } else {
                        ax = +0.26F; ay = +0.06F; az = -0.82F;
                        swordQ.set(Q_AIR1_PEAK);
                    }
                }
                case AIR_2_MID_SWEEP -> {
                    if (alpha < 0.24F) {
                        float u = DualBladeAnimState.easeOutQuart(alpha / 0.24F);
                        ax = Mth.lerp(u, +0.26F, +0.48F);
                        ay = Mth.lerp(u, +0.06F, -0.22F);
                        az = -0.80F;
                        slerpShortest(Q_AIR1_PEAK, Q_AIR2_START, u, swordQ);
                    } else if (alpha < 0.78F) {
                        float u = easeOutExpo((alpha - 0.24F) / 0.54F);
                        ax = Mth.lerp(u, +0.48F, -0.42F);
                        ay = -0.22F;
                        az = -0.86F;
                        slerpShortest(Q_AIR2_START, Q_AIR2_END, u, swordQ);
                    } else {
                        ax = -0.42F; ay = -0.22F; az = -0.86F;
                        swordQ.set(Q_AIR2_END);
                    }
                }
                case AIR_3_SLAM -> {
                    boolean landed = (entityId != -1) && KatanaAnimManager.isAirSlamLanded(entityId);
                    if (!landed) {
                        float u = DualBladeAnimState.easeOutQuart(Mth.clamp(alpha * 5.0F, 0.0F, 1.0F));
                        ax = Mth.lerp(u, -0.10F, +0.14F);
                        ay = Mth.lerp(u, -0.10F, +0.10F);
                        az = -0.80F;
                        slerpShortest(Q_AIR2_END, Q_AIR3_DIVE, u, swordQ);
                    } else if (alpha < 0.65F) {
                        float v = alpha / 0.65F;
                        float vib = Mth.sin(v * Mth.TWO_PI * 3.0F) * (1.0F - v);
                        ax = +0.10F; ay = -0.46F + vib * 0.02F; az = -0.86F;
                        swordQ.set(Q_AIR3_IMPACT).rotateLocalX(vib * 3.0F * Mth.DEG_TO_RAD);
                    } else {
                        float u = DualBladeAnimState.easeInOutCubic((alpha - 0.65F) / 0.35F);
                        ax = Mth.lerp(u, +0.10F, 0.56F);
                        ay = Mth.lerp(u, -0.46F, -0.66F);
                        az = Mth.lerp(u, -0.86F, -0.82F);
                        asc = Mth.lerp(u, 0.96F, 1.00F);
                        slerpShortest(Q_AIR3_IMPACT, Q_DRAWN_IDLE, u, swordQ);
                    }
                }
                case DEFLECT -> {
                    // Thế 3 — Suriage / Sekiro Parry: GĐ 1 hạ thấp hông trái (0..30%) -> GĐ 2 hất chéo dưới trái lên trên phải (bám mép dưới)!
                    if (alpha < 0.30F) {
                        ax = -0.22F; ay = -0.48F; az = -0.74F; asc = 1.02F;
                        swordQ.set(Q_SURIAGE_LOW);
                    } else {
                        float u = easeOutExpo((alpha - 0.30F) / 0.70F);
                        ax = Mth.lerp(u, -0.22F, +0.38F);
                        ay = Mth.lerp(u, -0.48F, -0.22F);
                        az = Mth.lerp(u, -0.74F, -0.80F);
                        asc = 1.02F;
                        slerpShortest(Q_SURIAGE_LOW, Q_SURIAGE_UP, u, swordQ);
                    }
                }
                case GUARD_KASA_IMPACT -> {
                    // Thế 2 — Kasa-uke Impact Frame: Mái che ngang trán neo chắc mép phải, sụp xuống hấp thụ lực rồi bật nảy!
                    float dip = (alpha < 0.44F)
                            ? DualBladeAnimState.easeOutQuart(alpha / 0.44F)
                            : (1.0F - DualBladeAnimState.easeInOutCubic((alpha - 0.44F) / 0.56F));
                    ax = +0.88F;
                    ay = +0.34F - dip * 0.12F;
                    az = -0.70F;
                    asc = 1.04F;
                    swordQ.set(Q_GUARD_KASA).rotateLocalX(-dip * 8.0F * Mth.DEG_TO_RAD);
                }
                case GUARD_MUNE_OSHI -> {
                    // Thế 4 — Mune-oshi: Dựng thẳng đứng chống đòn nặng, chuôi cắm gọn mép dưới không lơ lửng!
                    float fade = 1.0F - DualBladeAnimState.easeInOutCubic(Mth.clamp((alpha - 0.58F) / 0.42F, 0.0F, 1.0F));
                    float vib = Mth.sin((KatanaAnimManager.getClientTick() + partialTick) * 4.4F) * 0.014F * fade;
                    ax = +0.20F + vib * 0.5F;
                    ay = -0.52F + vib;
                    az = -0.72F + vib;
                    asc = 1.04F;
                    swordQ.set(Q_GUARD_MUNE);
                }
                case GUARD_SAYA_UKE_HIT -> {
                    // Thế 5 — Saya-uke: Nhấc vỏ + kiếm chắn chéo từ góc dưới-phải, chuôi bám mép dưới không lơ lửng!
                    float absorb = Mth.sin(alpha * Mth.PI);
                    ax = +0.52F;
                    ay = -0.54F - absorb * 0.05F;
                    az = -0.70F + absorb * 0.05F;
                    asc = 1.04F;
                    swordQ.set(Q_GUARD_SAYA).rotateLocalZ(absorb * 6.0F * Mth.DEG_TO_RAD);
                    renderSheathedSaya = true;
                }
                case PARRY_COUNTER -> {
                    if (alpha < 0.48F) {
                        float u = easeOutExpo(alpha / 0.48F);
                        ax = Mth.lerp(u, -0.26F, +0.52F);
                        ay = Mth.lerp(u, -0.30F, +0.02F);
                        az = -0.88F;
                        slerpShortest(Q_SURIAGE_UP, Q_COUNTER_END, u, swordQ);
                    } else if (alpha < 0.80F) {
                        ax = +0.52F; ay = +0.02F; az = -0.88F;
                        swordQ.set(Q_COUNTER_END);
                    } else {
                        float u = DualBladeAnimState.easeInOutCubic((alpha - 0.80F) / 0.20F);
                        ax = Mth.lerp(u, +0.52F, 0.56F);
                        ay = Mth.lerp(u, +0.02F, -0.66F);
                        az = Mth.lerp(u, -0.88F, -0.82F);
                        asc = Mth.lerp(u, 0.96F, 1.00F);
                        slerpShortest(Q_COUNTER_END, Q_DRAWN_IDLE, u, swordQ);
                    }
                }
                default -> swordQ.set(Q_DRAWN_IDLE);
            }

            poseStack.pushPose();
            poseStack.translate(0.5F - 0.56F, 0.5F + 0.52F, 0.5F + 0.72F);

            poseStack.pushPose();
            poseStack.translate(ax, ay, az);
            poseStack.mulPose(swordQ);
            poseStack.scale(asc, asc, asc);
            renderPart(swordData, poseStack, bufferSource, effectiveLight, packedOverlay, glowIntensity);
            if (renderSheathedSaya) {
                renderPart(scabbardData, poseStack, bufferSource, effectiveLight, packedOverlay, 0.74F);
            }
            poseStack.popPose();

            // Hiển thị cánh tay mang đúng skin người chơi (hỗ trợ Classic 4px, Alex Slim 3px & lớp áo tay ngoài)
            // nắm chặt chuôi tsuka khi vung chém, đâm, gạt phản đòn (2 tay Kenjutsu cho combo/xoay/không chiến/parry, 1 tay cho Thrust)!
            boolean showSwingHands = move != KatanaMoveType.GUARD_KASA_IMPACT
                    && move != KatanaMoveType.GUARD_MUNE_OSHI
                    && move != KatanaMoveType.GUARD_SAYA_UKE_HIT;
            if (showSwingHands) {
                boolean twoHandedGrip = move != KatanaMoveType.THRUST;
                boolean returnsToIdle = move == KatanaMoveType.HIT_2_DOWN_DIAG
                        || move == KatanaMoveType.HIT_3_UP_DIAG
                        || move == KatanaMoveType.HIT_4_FINISHER
                        || move == KatanaMoveType.THRUST
                        || move == KatanaMoveType.DELAY_SWEEP_360
                        || move == KatanaMoveType.PARRY_COUNTER
                        || (move == KatanaMoveType.AIR_3_SLAM && entityId != -1 && KatanaAnimManager.isAirSlamLanded(entityId));
                float retract = (returnsToIdle && alpha > 0.88F) ? ((alpha - 0.88F) / 0.12F) : 0.0F;
                renderFirstPersonPlayerArm(poseStack, bufferSource, entityId, false,
                        ax, ay, az, asc, +0.06F, swordQ, armLight, retract);
                if (twoHandedGrip) {
                    renderFirstPersonPlayerArm(poseStack, bufferSource, entityId, true,
                            ax, ay, az, asc, -0.15F, swordQ, armLight, retract);
                }
            }
            poseStack.popPose();
            return;
        }

        // 4) Dáng Idle & 3 Thế Đỡ Chủ Động (Uke-nagashi / Kasa-uke / Saya-uke)
        float kasa = (entityId != -1) ? KatanaAnimManager.getGuardKasaBlend(entityId, partialTick) : 0.0F;
        float sayaGuard = (entityId != -1) ? Math.max(
                KatanaAnimManager.getSayaGuardBlend(entityId, partialTick),
                KatanaAnimManager.getIaidoChargeBlend(entityId, partialTick)) : 0.0F;
        float ukeDip = (entityId != -1) ? Mth.sin(KatanaAnimManager.getUkeSlideDip(entityId) * Mth.PI) : 0.0F;

        float hx = Mth.lerp(drawBlend, -0.52F, 0.56F);
        float hy = Mth.lerp(drawBlend, -0.28F, -0.66F);
        float hz = Mth.lerp(drawBlend, -0.66F, -0.82F);
        float sc = Mth.lerp(drawBlend, 0.96F, 1.00F);

        Quaternionf idleQ = new Quaternionf();
        slerpShortest(Q_SHEATH_IDLE, Q_DRAWN_IDLE, drawBlend, idleQ);

        if (sprint > 0.001F && drawBlend > 0.5F && guard < 0.05F) {
            float s = sprint * drawBlend;
            hx += 0.04F * s;
            hy -= 0.04F * s;
            hz -= 0.03F * s;
            idleQ.rotateLocalZ(-6.0F * s * Mth.DEG_TO_RAD).rotateLocalX(-5.0F * s * Mth.DEG_TO_RAD);
        }

        // Thế 5: Saya-uke (Nhấc cả vỏ + kiếm chắn chéo từ góc dưới-phải, chuôi cắm sâu quá mép dưới không lơ lửng!)
        if (sayaGuard > 0.001F && drawBlend < 0.5F) {
            hx = Mth.lerp(sayaGuard, hx, +0.52F);
            hy = Mth.lerp(sayaGuard, hy, -0.54F);
            hz = Mth.lerp(sayaGuard, hz, -0.70F);
            sc = Mth.lerp(sayaGuard, sc, 1.04F);
            Quaternionf tmp = new Quaternionf();
            slerpShortest(idleQ, Q_GUARD_SAYA, sayaGuard, tmp);
            idleQ.set(tmp);
        }

        // Thế 1 (Uke-nagashi 45° — chuôi bám gọn mép dưới) <-> Thế 2 (Kasa-uke mái che — chuôi bám mép phải, cả ở FOV 90°!)
        if (guard > 0.001F) {
            float gHx = Mth.lerp(kasa, +0.08F, +0.88F);
            float gHy = Mth.lerp(kasa, -0.50F - ukeDip * 0.04F, +0.34F);
            float gHz = Mth.lerp(kasa, -0.72F + ukeDip * 0.04F, -0.70F);
            Quaternionf guardQ = new Quaternionf();
            slerpShortest(Q_GUARD_UKE, Q_GUARD_KASA, kasa, guardQ);
            if (ukeDip > 0.01F && kasa < 0.5F) {
                guardQ.rotateLocalZ(ukeDip * 8.0F * Mth.DEG_TO_RAD);
            }
            hx = Mth.lerp(guard, hx, gHx);
            hy = Mth.lerp(guard, hy, gHy);
            hz = Mth.lerp(guard, hz, gHz);
            sc = Mth.lerp(guard, sc, 1.04F);
            Quaternionf tmp = new Quaternionf();
            slerpShortest(idleQ, guardQ, guard, tmp);
            idleQ.set(tmp);
        }

        float side = isLeftHand ? -1.0F : 1.0F;
        float camHx = side * hx;

        poseStack.pushPose();
        poseStack.translate(0.5F - side * 0.56F, 0.5F + 0.52F, 0.5F + 0.72F);

        poseStack.pushPose();
        poseStack.translate(camHx, hy, hz);
        poseStack.mulPose(idleQ);
        poseStack.scale(sc, sc, sc);
        renderPart(swordData, poseStack, bufferSource, effectiveLight, packedOverlay, glowIntensity);
        if (drawBlend < 0.72F) {
            poseStack.pushPose();
            poseStack.translate(0.0F, +drawBlend * 1.45F, 0.0F);
            renderPart(scabbardData, poseStack, bufferSource, effectiveLight, packedOverlay, 0.72F);
            poseStack.popPose();
        }
        poseStack.popPose();
        poseStack.popPose();
    }

    /**
     * Render cánh tay thật (`PlayerRenderer#renderRightHand / renderLeftHand`) dùng đúng skin & lớp áo tay ngoài
     * của `AbstractClientPlayer` (hỗ trợ cả Classic 4px & Slim Alex 3px) nắm chặt chuôi Katana (`tsuka`) hoặc vỏ `Saya`
     * khi vung chém ở góc nhìn thứ nhất — tự động kéo dài trục cánh tay ở các đòn vươn xa (như `THRUST`) để không hở đáy!
     */
    private static void renderFirstPersonPlayerArm(PoseStack poseStack, MultiBufferSource bufferSource,
                                                   int entityId, boolean isLeftHand,
                                                   float hx, float hy, float hz, float swordScale,
                                                   float gripLocalY, Quaternionf swordQ,
                                                   int packedLight, float retractFrac) {
        if (retractFrac >= 0.99F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer acp = null;
        if (mc.level != null && entityId != -1 && mc.level.getEntity(entityId) instanceof AbstractClientPlayer p) {
            acp = p;
        } else if (mc.player != null) {
            acp = mc.player;
        }
        if (acp == null || acp.isInvisible()) {
            return;
        }
        if (!(mc.getEntityRenderDispatcher().getRenderer(acp) instanceof PlayerRenderer playerRenderer)) {
            return;
        }

        Vector3f gripCam = new Vector3f(0.0F, gripLocalY * swordScale, 0.0F);
        swordQ.transform(gripCam).add(hx, hy, hz);

        float side = isLeftHand ? -1.0F : 1.0F;
        Vector3f shoulderCam = new Vector3f(side * 0.60F, -0.78F, +0.06F);

        Vector3f delta = new Vector3f(gripCam).sub(shoulderCam);
        float dist = delta.length();
        if (dist < 1e-4F) {
            return;
        }
        Vector3f yArm = new Vector3f(delta).div(dist);

        // Thu mượt cánh tay xuống dưới mép màn hình ở 12% cuối khi trở về dáng chờ Idle (0 giật biến mất đột ngột!)
        float retract = Mth.clamp(retractFrac, 0.0F, 1.0F);
        if (retract > 0.001F) {
            gripCam.fma(-retract * 0.32F, yArm);
        }

        Vector3f upHint = (Math.abs(yArm.y) > 0.92F) ? new Vector3f(0.0F, 0.0F, -1.0F) : new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f xArm = new Vector3f(upHint).cross(yArm).normalize();
        Vector3f zArm = new Vector3f(xArm).cross(yArm).normalize();
        Quaternionf armQ = new Matrix3f()
                .setColumn(0, xArm)
                .setColumn(1, yArm)
                .setColumn(2, zArm)
                .getNormalizedRotation(new Quaternionf());

        boolean slim = (acp.getSkin().model() == PlayerSkin.Model.SLIM);
        float fistOffX = (isLeftHand ? -1.0F : 1.0F) * (slim ? (5.5F / 16.0F) : (6.0F / 16.0F));
        float fistOffY = slim ? (-11.0F / 16.0F) : (-10.5F / 16.0F);
        float armScaleXZ = 0.68F;
        // Kéo dài mượt trục Y cánh tay khi đâm vươn xa (THRUST az=-1.26) để phần vai luôn neo kín dưới mép màn hình!
        float armScaleY = Mth.clamp((dist / (10.5F / 16.0F)) * 0.96F, armScaleXZ, 1.38F);

        poseStack.pushPose();
        poseStack.translate(gripCam.x, gripCam.y, gripCam.z);
        poseStack.mulPose(armQ);
        poseStack.scale(armScaleXZ, armScaleY, armScaleXZ);
        poseStack.translate(fistOffX, fistOffY, 0.0F);
        if (isLeftHand) {
            playerRenderer.renderLeftHand(poseStack, bufferSource, packedLight, acp);
        } else {
            playerRenderer.renderRightHand(poseStack, bufferSource, packedLight, acp);
        }
        poseStack.popPose();
    }

    public static void renderHipScabbard(PoseStack poseStack, MultiBufferSource bufferSource,
                                         int packedLight, int packedOverlay) {
        renderHipScabbard(poseStack, bufferSource, packedLight, packedOverlay, 0.0F, 0.0F);
    }

    public static void renderHipScabbard(PoseStack poseStack, MultiBufferSource bufferSource,
                                         int packedLight, int packedOverlay,
                                         float pitchDeltaDeg, float offZ) {
        ensureMeshLoaded();
        if (scabbardData == null || scabbardData.length == 0) {
            return;
        }
        int blk = Math.max(LightTexture.block(packedLight), 10);
        int sky = Math.max(LightTexture.sky(packedLight), 12);
        int effectiveLight = LightTexture.pack(blk, sky);

        poseStack.pushPose();
        poseStack.translate(0.31F, 0.62F, -0.06F + offZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(14.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(76.0F + pitchDeltaDeg));
        poseStack.mulPose(Axis.ZP.rotationDegrees(-10.0F));
        poseStack.scale(0.92F, 0.92F, 0.92F);
        poseStack.translate(0.0F, -0.20F, 0.0F);
        renderPart(scabbardData, poseStack, bufferSource, effectiveLight, packedOverlay, 0.72F);
        poseStack.popPose();
    }

    private static void slerpShortest(Quaternionf a, Quaternionf b, float t, Quaternionf out) {
        float dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w;
        if (dot < 0.0F) {
            out.set(-b.x, -b.y, -b.z, -b.w);
            a.slerp(out, Mth.clamp(t, 0.0F, 1.0F), out);
        } else {
            a.slerp(b, Mth.clamp(t, 0.0F, 1.0F), out);
        }
    }

    private static float easeOutExpo(float t) {
        float x = Mth.clamp(t, 0.0F, 1.0F);
        return (x >= 1.0F) ? 1.0F : (1.0F - (float) Math.pow(2.0, -10.0 * x));
    }

    private static void renderPart(float[] data, PoseStack poseStack, MultiBufferSource bufferSource,
                                   int packedLight, int packedOverlay, float glowIntensity) {
        renderPartClippedY(data, poseStack, bufferSource, packedLight, packedOverlay, glowIntensity, 10.0F);
    }

    private static void renderPartClippedY(float[] data, PoseStack poseStack, MultiBufferSource bufferSource,
                                           int packedLight, int packedOverlay, float glowIntensity, float maxY) {
        if (data == null || data.length == 0) {
            return;
        }
        PoseStack.Pose pose = poseStack.last();
        boolean clip = (maxY < 2.0F);

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEX_KATANA));
        if (clip) {
            emitMeshClippedY(vc, pose, data, 255, 255, 255, 255, packedLight, packedOverlay, maxY);
        } else {
            emitMesh(vc, pose, data, 255, 255, 255, 255, packedLight, packedOverlay);
        }

        int ch = Mth.clamp(Math.round(255.0F * glowIntensity), 0, 255);
        if (ch > 4) {
            VertexConsumer gvc = bufferSource.getBuffer(RenderType.eyes(GLOW_KATANA));
            if (clip) {
                emitMeshClippedY(gvc, pose, data, ch, ch, ch, 255, LightTexture.FULL_BRIGHT, packedOverlay, maxY);
            } else {
                emitMesh(gvc, pose, data, ch, ch, ch, 255, LightTexture.FULL_BRIGHT, packedOverlay);
            }
        }
    }

    private static void emitMesh(VertexConsumer vc, PoseStack.Pose pose, float[] data,
                                 int r, int g, int b, int a, int packedLight, int packedOverlay) {
        for (int i = 0; i < data.length; i += 8) {
            vc.addVertex(pose, data[i], data[i + 1], data[i + 2])
                    .setColor(r, g, b, a)
                    .setUv(data[i + 3], data[i + 4])
                    .setOverlay(packedOverlay)
                    .setLight(packedLight)
                    .setNormal(pose, data[i + 5], data[i + 6], data[i + 7]);
        }
    }

    private static void emitMeshClippedY(VertexConsumer vc, PoseStack.Pose pose, float[] data,
                                         int r, int g, int b, int a, int packedLight, int packedOverlay, float maxY) {
        for (int q = 0; q < data.length; q += 32) {
            int o0 = q;
            int o1 = q + 8;
            int o2 = q + 16;
            boolean in0 = data[o0 + 1] <= maxY;
            boolean in1 = data[o1 + 1] <= maxY;
            boolean in2 = data[o2 + 1] <= maxY;
            int inside = (in0 ? 1 : 0) + (in1 ? 1 : 0) + (in2 ? 1 : 0);
            if (inside == 0) {
                continue;
            }
            if (inside == 3) {
                emitVert(vc, pose, data, o0, r, g, b, a, packedLight, packedOverlay);
                emitVert(vc, pose, data, o1, r, g, b, a, packedLight, packedOverlay);
                emitVert(vc, pose, data, o2, r, g, b, a, packedLight, packedOverlay);
                emitVert(vc, pose, data, q + 24, r, g, b, a, packedLight, packedOverlay);
                continue;
            }
            if (inside == 1) {
                int aO = in0 ? o0 : (in1 ? o1 : o2);
                int bO = in0 ? o1 : (in1 ? o2 : o0);
                int cO = in0 ? o2 : (in1 ? o0 : o1);
                emitVert(vc, pose, data, aO, r, g, b, a, packedLight, packedOverlay);
                emitLerpVert(vc, pose, data, aO, bO, maxY, r, g, b, a, packedLight, packedOverlay);
                emitLerpVert(vc, pose, data, aO, cO, maxY, r, g, b, a, packedLight, packedOverlay);
                emitLerpVert(vc, pose, data, aO, cO, maxY, r, g, b, a, packedLight, packedOverlay);
            } else {
                int cO = !in0 ? o0 : (!in1 ? o1 : o2);
                int aO = !in0 ? o1 : (!in1 ? o2 : o0);
                int bO = !in0 ? o2 : (!in1 ? o0 : o1);
                emitVert(vc, pose, data, aO, r, g, b, a, packedLight, packedOverlay);
                emitVert(vc, pose, data, bO, r, g, b, a, packedLight, packedOverlay);
                emitLerpVert(vc, pose, data, bO, cO, maxY, r, g, b, a, packedLight, packedOverlay);
                emitLerpVert(vc, pose, data, aO, cO, maxY, r, g, b, a, packedLight, packedOverlay);
            }
        }
    }

    private static void emitVert(VertexConsumer vc, PoseStack.Pose pose, float[] d, int o,
                                 int r, int g, int b, int a, int light, int overlay) {
        vc.addVertex(pose, d[o], d[o + 1], d[o + 2])
                .setColor(r, g, b, a)
                .setUv(d[o + 3], d[o + 4])
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, d[o + 5], d[o + 6], d[o + 7]);
    }

    private static void emitLerpVert(VertexConsumer vc, PoseStack.Pose pose, float[] d, int iA, int iB, float maxY,
                                     int r, int g, int b, int a, int light, int overlay) {
        float yA = d[iA + 1];
        float yB = d[iB + 1];
        float denom = yB - yA;
        float t = (Math.abs(denom) > 1e-6F) ? Mth.clamp((maxY - yA) / denom, 0.0F, 1.0F) : 0.0F;
        vc.addVertex(pose,
                        Mth.lerp(t, d[iA], d[iB]),
                        maxY,
                        Mth.lerp(t, d[iA + 2], d[iB + 2]))
                .setColor(r, g, b, a)
                .setUv(Mth.lerp(t, d[iA + 3], d[iB + 3]), Mth.lerp(t, d[iA + 4], d[iB + 4]))
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, d[iA + 5], d[iA + 6], d[iA + 7]);
    }

    private static boolean isGuiStackDrawn(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        var lp = mc.player;
        if (lp != null && stack == lp.getMainHandItem()) {
            if (KatanaAnimManager.isMoveActive(lp.getId(), KatanaMoveType.CHIBURI_NOTO)) {
                float pt = mc.getTimer().getGameTimeDeltaPartialTick(true);
                return KatanaAnimManager.getDrawBlend(lp.getId(), pt) > 0.10F;
            }
            return KatanaClientState.isDrawn(lp.getId(), stack);
        }
        return false;
    }

    private static void renderCrispGuiIcon(ItemStack stack, PoseStack poseStack, MultiBufferSource bufferSource) {
        poseStack.pushPose();
        ResourceLocation iconTex = isGuiStackDrawn(stack) ? GUI_ICON_DRAWN : GUI_ICON_SHEATHED;
        RenderType type = RenderType.entityTranslucent(iconTex);
        VertexConsumer vc = ItemRenderer.getFoilBufferDirect(bufferSource, type, true, stack.hasFoil());
        PoseStack.Pose pose = poseStack.last();
        float z = 0.5F;

        vc.addVertex(pose, 0.0F, 0.0F, z)
                .setColor(255, 255, 255, 255).setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, 1.0F, 0.0F, z)
                .setColor(255, 255, 255, 255).setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, 1.0F, 1.0F, z)
                .setColor(255, 255, 255, 255).setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        vc.addVertex(pose, 0.0F, 1.0F, z)
                .setColor(255, 255, 255, 255).setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 0.0F, 1.0F);
        poseStack.popPose();
    }
}
