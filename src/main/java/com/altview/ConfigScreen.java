package com.altview;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class ConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget ipField;

    public ConfigScreen(Screen parent) {
        super(Text.literal("AltView & Stasis (Xiaomi Pad / 1.21.11)"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = 24;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Роль: " + ModConfig.INSTANCE.clientRole.displayName),
                btn -> {
                    ModConfig.Role[] roles = ModConfig.Role.values();
                    ModConfig.INSTANCE.clientRole = roles[(ModConfig.INSTANCE.clientRole.ordinal() + 1) % roles.length];
                    btn.setMessage(Text.literal("Роль: " + ModConfig.INSTANCE.clientRole.displayName));
                    ModConfig.save();
                    AltNetworkManager.restartNetworking();
                }).dimensions(centerX - 115, y, 230, 20).build());
        y += 24;

        this.ipField = new TextFieldWidget(this.textRenderer, centerX - 115, y, 150, 20, Text.literal("IP основы"));
        this.ipField.setMaxLength(32);
        this.ipField.setText(ModConfig.INSTANCE.targetHostIp);
        this.addDrawableChild(this.ipField);

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Сохранить IP"),
                btn -> {
                    ModConfig.INSTANCE.targetHostIp = this.ipField.getText().trim();
                    ModConfig.save();
                    AltNetworkManager.restartNetworking();
                }).dimensions(centerX + 40, y, 75, 20).build());
        y += 24;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Масштаб GPU: 1/" + ModConfig.INSTANCE.downscaleFactor + " | FPS: " + ModConfig.INSTANCE.captureFps),
                btn -> {
                    if (ModConfig.INSTANCE.downscaleFactor == 1) ModConfig.INSTANCE.downscaleFactor = 2;
                    else if (ModConfig.INSTANCE.downscaleFactor == 2) ModConfig.INSTANCE.downscaleFactor = 4;
                    else ModConfig.INSTANCE.downscaleFactor = 1;
                    btn.setMessage(Text.literal("Масштаб GPU: 1/" + ModConfig.INSTANCE.downscaleFactor + " | FPS: " + ModConfig.INSTANCE.captureFps));
                    ModConfig.save();
                }).dimensions(centerX - 115, y, 230, 20).build());
        y += 24;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Позиция Твинка 1: X=" + ModConfig.INSTANCE.alt1X + " Y=" + ModConfig.INSTANCE.alt1Y),
                btn -> {
                    ModConfig.INSTANCE.alt1X = (ModConfig.INSTANCE.alt1X + 70) % Math.max(80, this.width - ModConfig.INSTANCE.alt1Width);
                    btn.setMessage(Text.literal("Позиция Твинка 1: X=" + ModConfig.INSTANCE.alt1X + " Y=" + ModConfig.INSTANCE.alt1Y));
                    ModConfig.save();
                }).dimensions(centerX - 115, y, 230, 20).build());
        y += 24;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Позиция Твинка 2: X=" + ModConfig.INSTANCE.alt2X + " Y=" + ModConfig.INSTANCE.alt2Y),
                btn -> {
                    ModConfig.INSTANCE.alt2X = (ModConfig.INSTANCE.alt2X + 70) % Math.max(80, this.width - ModConfig.INSTANCE.alt2Width);
                    btn.setMessage(Text.literal("Позиция Твинка 2: X=" + ModConfig.INSTANCE.alt2X + " Y=" + ModConfig.INSTANCE.alt2Y));
                    ModConfig.save();
                }).dimensions(centerX - 115, y, 230, 20).build());
        y += 24;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Фиксация камеры твинка: " + (ModConfig.INSTANCE.lockAltCamera ? "ВКЛ" : "ВЫКЛ")),
                btn -> {
                    ModConfig.INSTANCE.lockAltCamera = !ModConfig.INSTANCE.lockAltCamera;
                    if (ModConfig.INSTANCE.lockAltCamera && MinecraftClient.getInstance().player != null) {
                        ModConfig.INSTANCE.lockedYaw = MinecraftClient.getInstance().player.getYaw();
                        ModConfig.INSTANCE.lockedPitch = MinecraftClient.getInstance().player.getPitch();
                    }
                    btn.setMessage(Text.literal("Фиксация камеры твинка: " + (ModConfig.INSTANCE.lockAltCamera ? "ВКЛ" : "ВЫКЛ")));
                    ModConfig.save();
                }).dimensions(centerX - 115, y, 230, 20).build());
        y += 28;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Готово"),
                btn -> this.client.setScreen(this.parent)
        ).dimensions(centerX - 60, y, 120, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFFFF);
    }
}