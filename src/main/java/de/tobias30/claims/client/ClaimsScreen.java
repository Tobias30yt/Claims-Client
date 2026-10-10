package de.tobias30.claims.client;

import de.tobias30.claims.shared.Wire;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;

/** Compact native vanilla screen with centered, singleplayer-sized controls. */
final class ClaimsScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;
    private final Wire.Menu menu;
    private EditBox input;
    private int page;
    private String value = "";
    private boolean sent;

    ClaimsScreen(Wire.Menu menu) { super(Component.literal(menu.title())); this.menu = menu; }

    @Override protected void init() {
        int width = Math.min(BUTTON_WIDTH, this.width - 24), left = (this.width - width) / 2;
        int entryCount = menu.entryCount();
        List<Integer> footer = new ArrayList<>();
        boolean needsInput = false;
        int serverPrevious = -1, serverNext = -1;
        for (int i = entryCount; i < menu.buttons().size(); i++) {
            Wire.Button item = menu.buttons().get(i);
            if (item.label().equalsIgnoreCase("Previous page")) serverPrevious = i;
            else if (item.label().equalsIgnoreCase("Next page")) serverNext = i;
            else { footer.add(i); needsInput |= item.input(); }
        }
        int footerRows = Math.max(1, (footer.size() + 1) / 2);
        int footerTop = this.height - 34 - footerRows * 25;
        int inputY = needsInput ? footerTop - 26 : footerTop;
        int listTop = 68, listBottom = inputY - (needsInput ? 18 : 8);
        int perPage = Math.max(1, (listBottom - listTop) / 24);
        int totalPages = Math.max(1, (entryCount + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, totalPages - 1));

        for (int i = page * perPage; i < Math.min(entryCount, (page + 1) * perPage); i++) {
            Wire.Button entry = menu.buttons().get(i);
            Component label = Component.literal(shorten(entry.label(), width - 18)).withStyle(entry.danger() ? ChatFormatting.RED : ChatFormatting.WHITE);
            int actionIndex = i;
            Button button = Button.builder(label, b -> send(actionIndex)).bounds(left, listTop + (i - page * perPage) * 24, width, 20).build();
            button.setTooltip(Tooltip.create(Component.literal(entry.label() + "\n" + entry.detail())));
            addRenderableWidget(button);
        }

        if (entryCount > 0 || serverPrevious >= 0 || serverNext >= 0) {
            int previousAction = serverPrevious, nextAction = serverNext;
            Button previous = Button.builder(Component.literal("‹"), b -> {
                if (page > 0) { page--; rebuildWidgets(); } else if (previousAction >= 0) send(previousAction);
            }).bounds(left, 39, 32, 20).build();
            previous.active = page > 0 || serverPrevious >= 0;
            previous.setTooltip(Tooltip.create(Component.literal("Previous")));
            addRenderableWidget(previous);
            Button next = Button.builder(Component.literal("›"), b -> {
                if (page + 1 < totalPages) { page++; rebuildWidgets(); } else if (nextAction >= 0) send(nextAction);
            }).bounds(left + width - 32, 39, 32, 20).build();
            next.active = page + 1 < totalPages || serverNext >= 0;
            next.setTooltip(Tooltip.create(Component.literal("Next")));
            addRenderableWidget(next);
        }

        if (needsInput) {
            input = new EditBox(font, (this.width - width) / 2, inputY, width, 20, Component.literal("Enter a name"));
            input.setMaxLength(64); input.setHint(Component.literal("Enter a player or home name…")); input.setValue(value);
            input.setResponder(text -> value = text); addRenderableWidget(input);
        }
        int columns = Math.min(2, Math.max(1, footer.size()));
        int gap = 6, buttonWidth = Math.min(BUTTON_WIDTH, (this.width - 24 - gap * (columns - 1)) / columns);
        for (int n = 0; n < footer.size(); n++) {
            int index = footer.get(n), row = n / columns, col = n % columns;
            int y = footerTop + row * 25;
            int x = (this.width - (columns * buttonWidth + (columns - 1) * gap)) / 2 + col * (buttonWidth + gap);
            Wire.Button action = menu.buttons().get(index);
            Component label = Component.literal(shorten(action.label(), buttonWidth - 12)).withStyle(action.danger() ? ChatFormatting.RED : action.input() ? ChatFormatting.GREEN : ChatFormatting.WHITE);
            Button button = Button.builder(label, b -> send(index)).bounds(x, y, buttonWidth, 20).build();
            button.setTooltip(Tooltip.create(Component.literal(action.detail() + (action.input() ? "\nEnter the answer above." : ""))));
            addRenderableWidget(button);
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds((this.width - width) / 2, this.height - 26, width, 20).build());
    }

    private String shorten(String text, int width) { return font.plainSubstrByWidth(text, Math.max(24, width)); }
    private void send(int index) {
        if (sent || !ClientPlayNetworking.canSend(Payloads.Action.TYPE)) return;
        if (menu.buttons().get(index).input() && value.isBlank()) { if (input != null) setFocused(input); return; }
        sent = true;
        ClientPlayNetworking.send(new Payloads.Action(Wire.action(new Wire.Action(menu.session(), index, menu.buttons().get(index).input() ? value.trim() : ""))));
        onClose();
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, title, width / 2, 14, 0xFFFFFFFF);
        graphics.centeredText(font, font.plainSubstrByWidth(menu.subtitle(), Math.max(40, width - 24)), width / 2, 35, 0xFFCCCCCC);
        if (menu.entryCount() == 0 && footerCount() == 0) graphics.centeredText(font, "Nothing here yet.", width / 2, height / 2, 0xFFB8C0CC);
        if (menu.entryCount() > 0) {
            int count = Math.max(1, (height - 105) / 24);
            int pages = Math.max(1, (menu.entryCount() + count - 1) / count);
            graphics.centeredText(font, (page + 1) + " / " + pages, width / 2, 44, 0xFFB8C0CC);
        }
    }
    private int footerCount() { return menu.buttons().size() - menu.entryCount(); }
    @Override public boolean isPauseScreen() { return false; }
}
