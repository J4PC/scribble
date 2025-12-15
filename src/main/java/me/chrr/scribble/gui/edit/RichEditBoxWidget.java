package me.chrr.scribble.gui.edit;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import me.chrr.scribble.book.RichText;
import me.chrr.scribble.history.command.EditCommand;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Tuple;
import net.minecraft.util.Util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.ArrayList;
import me.chrr.scribble.Scribble;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import me.chrr.scribble.history.command.Command;

public class RichEditBoxWidget extends MultiLineEditBox {
    @Nullable
    private final Runnable onInvalidateFormat;
    @Nullable
    private final Consumer<EditCommand> onHistoryPush;
    @Nullable
    private final Consumer<Command> onCommandPush;
    @Nullable
    private final PageOverflowHandler onPageOverflow;
    @Nullable
    private final Consumer<Boolean> onInsertPage; // true = insert BEFORE (left) current page, false = insert AFTER (right) current page
    @Nullable
    private final Consumer<Boolean> onDeletePage; // true = go back, false = stay

    @Nullable
    public ChatFormatting color = ChatFormatting.BLACK;
    public Set<ChatFormatting> modifiers = new HashSet<>();

    private RichEditBoxWidget(Font font, int x, int y, int width, int height,
                              Component placeholder, Component message, int textColor, boolean textShadow, int cursorColor,
                              boolean hasBackground, boolean hasOverlay,
                              @Nullable Runnable onInvalidateFormat, @Nullable Consumer<EditCommand> onHistoryPush,
                              @Nullable PageOverflowHandler onPageOverflow, @Nullable Consumer<Boolean> onInsertPage, @Nullable Consumer<Boolean> onDeletePage,
                              @Nullable Consumer<Command> onCommandPush) {
        super(font, x, y, width, height, placeholder, message, textColor, textShadow, cursorColor, hasBackground, hasOverlay);
        this.onInvalidateFormat = onInvalidateFormat;
        this.onHistoryPush = onHistoryPush;
        this.onPageOverflow = onPageOverflow;
        this.onInsertPage = onInsertPage;
        this.onDeletePage = onDeletePage;
        this.onCommandPush = onCommandPush;

        this.textField = new RichMultiLineTextField(
                font, width - this.totalInnerPadding(),
                () -> new Tuple<>(Optional.ofNullable(color).orElse(ChatFormatting.BLACK), modifiers),
                (color, modifiers) -> {
                    this.color = color;
                    this.modifiers = new HashSet<>(modifiers);
                    this.notifyInvalidateFormat();
                });
    }
    
    @FunctionalInterface //Interface for handling page overflow when text exceeds page limit
    public interface PageOverflowHandler {
        boolean handleOverflow(RichText remainingContent, RichText overflowContent, int cursorPos);
    }

    private void notifyInvalidateFormat() {
        if (this.onInvalidateFormat != null) {
            this.onInvalidateFormat.run();
        }
    }

    private void pushHistory(EditCommand command) {
        if (this.onHistoryPush != null) {
            this.onHistoryPush.accept(command);
        }
    }

    public void applyFormatting(ChatFormatting formatting, boolean active) {
        RichMultiLineTextField editBox = this.getRichTextField();

        if (editBox.hasSelection()) {
            EditCommand command = new EditCommand(editBox, (box) -> box.applyFormatting(formatting, active));
            command.executeEdit(editBox);
            this.pushHistory(command);
        } else {
            if (formatting.isFormat()) {
                if (active) {
                    this.modifiers.add(formatting);
                } else {
                    this.modifiers.remove(formatting);
                }
            } else {
                this.color = formatting;
            }

            this.notifyInvalidateFormat();
        }
    }

    private int getCursorColor() {
        if (this.color == null) {
            return CommonColors.BLACK;
        } else {
            //noinspection DataFlowIssue: the color variable is never a modifier.
            return 0xff000000 | this.color.getColor();
        }
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float deltaTicks) {
        RichText text = getRichTextField().getRichText();

        // Draw the placeholder text if there's no content.
        if (text.isEmpty() && !this.isFocused()) {
            graphics.drawWordWrap(this.font, this.placeholder, this.getInnerLeft(), this.getInnerTop(), this.width - this.totalInnerPadding(), -857677600);
            return;
        }

        int cursor = this.textField.cursor();
        boolean blink = this.isFocused() && (Util.getMillis() - this.focusedTime) / 300L % 2L == 0L;
        boolean cursorInText = cursor < text.getLength();

        int lastX = 0;
        int lastY = 0;

        int y = this.getInnerTop();
        boolean hasDrawnCursor = false;
        for (MultilineTextField.StringView line : this.textField.iterateLines()) {
            boolean visible = this.withinContentAreaTopBottom(y, y + font.lineHeight);

            int x = this.getInnerLeft();
            if (blink && cursorInText && cursor >= line.beginIndex() && cursor <= line.endIndex()) {
                if (visible) {
                    // AD-HOC: Draw the entire line in one call. Vanilla does this differently, I don't know why
                    RichText lineText = text.subText(line.beginIndex(), line.endIndex());
                    graphics.drawString(this.font, lineText.getAsMutableComponent(), x, y, this.textColor, this.textShadow);

                    RichText beforeCursor = text.subText(line.beginIndex(), cursor);
                    lastX = x + this.font.width(beforeCursor);

                    if (!hasDrawnCursor) {
                        graphics.fill(lastX, y - 1, lastX + 1, y + 1 + this.font.lineHeight, this.getCursorColor());
                        hasDrawnCursor = true;
                    }
                }
            } else {
                // Otherwise, just draw the line normally.
                if (visible) {
                    RichText lineText = text.subText(line.beginIndex(), line.endIndex());
                    graphics.drawString(this.font, lineText.getAsMutableComponent(), x, y, this.textColor, this.textShadow);
                    lastX = x + this.font.width(lineText) - 1;
                }

                lastY = y;
            }

            y += this.font.lineHeight;
        }

        // If we haven't drawn the cursor yet, it should be a '_' at the last draw position.
        if (blink && !cursorInText) {
            if (this.withinContentAreaTopBottom(lastY, lastY + this.font.lineHeight)) {
                graphics.drawString(this.font, "_", lastX + 1, lastY, this.getCursorColor(), this.textShadow);
            }
        }

        // If we have a selection, we want to draw it.
        if (this.textField.hasSelection()) {
            MultilineTextField.StringView selection = this.textField.getSelected();
            int x = this.getInnerLeft();
            y = this.getInnerTop();

            // Loop through the lines, and draw selection boxes for each line.
            for (MultilineTextField.StringView line : this.textField.iterateLines()) {
                if (selection.beginIndex() <= line.endIndex()) {
                    if (line.beginIndex() > selection.endIndex()) {
                        break;
                    }

                    if (this.withinContentAreaTopBottom(y, y + this.font.lineHeight)) {
                        int start = this.font.width(text.subText(line.beginIndex(), Math.max(selection.beginIndex(), line.beginIndex())));

                        int end = selection.endIndex() > line.endIndex()
                                ? this.width - this.innerPadding()
                                : this.font.width(text.subText(line.beginIndex(), selection.endIndex()));

                        graphics.textHighlight(x + start, y, x + end, y + this.font.lineHeight, true);
                    }
                }

                y += this.font.lineHeight;
            }
        }

        // Switch the cursor to an I-beam when we're hovering.
        if (this.isHovered()) {
            graphics.requestCursor(CursorTypes.IBEAM);
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (this.visible && this.isFocused() && event.isAllowedChatCharacter()) {
            // Check if we need to handle page overflow
            if (onPageOverflow != null && handlePotentialOverflow(event.codepointAsString())) {
                return true;
            }
            
            EditCommand command = new EditCommand(this.getRichTextField(),
                    (editBox) -> editBox.insertText(event.codepointAsString()));
            command.executeEdit(this.getRichTextField());
            this.pushHistory(command);
            return true;
        } else {
            return false;
        }
    }

    // Check if typing this character would cause overflow, and handle it if so
    private boolean handlePotentialOverflow(String newChar) {
        RichMultiLineTextField textField = getRichTextField();
        RichText currentText = textField.getRichText();
        int cursor = textField.cursor();
        
        if (cursor != currentText.getLength()) { //Cursor at the very end of the page
            //The following fixes the edge cases where there is a space after the point where the user types and when there are formatting codes after the cursor.
            String textAfterCursor = currentText.subText(cursor, currentText.getLength()).getPlainText();
            if (!textAfterCursor.trim().isEmpty()) {
                return false;
            }
        }
        // Count lines and get last line in one pass
        int lineCount = 0;
        MultilineTextField.StringView lastLine = null;
        for (MultilineTextField.StringView line : this.textField.iterateLines()) {
            lastLine = line;
            lineCount++;
        }  
        if (lineCount < textField.lineLimit) {  
            return false; // Not at line limit yet: No overflow possible
        }
        
        // At line limit: Check if adding char would cause the last line to wrap
        String plainText = currentText.getPlainText();
        String lastLineText = plainText.substring(lastLine.beginIndex(), lastLine.endIndex());
        if (font.width(lastLineText + newChar) <= textField.width) {
            return false; // Still fits on current line
        }
        
        // Overflow confirmed: Create styled new char and handle split
        RichText newCharText = new RichText(newChar, color != null ? color : ChatFormatting.BLACK, modifiers);
        // Two cases for splitting:
        // 1. No space in last line (e.g. long word/ornamentation): only new char moves
        // 2. Has space: break at last space to keep word intact
        int lastSpace = lastLineText.lastIndexOf(' ');
        if (lastSpace == -1) {
            return onPageOverflow.handleOverflow(currentText, newCharText, 1);
        } else {
            int splitPoint = lastLine.beginIndex() + lastSpace + 1;
            RichText remainingContent = currentText.subText(0, splitPoint);
            RichText overflowContent = currentText.subText(splitPoint, plainText.length())
                .insert(plainText.length() - splitPoint, newCharText);
            return onPageOverflow.handleOverflow(remainingContent, overflowContent, overflowContent.getLength());
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isPaste() && this.onCommandPush != null) {
            String clipboard = Minecraft.getInstance().keyboardHandler.getClipboard();
            if (clipboard == null || clipboard.isEmpty()) return true;

            RichMultiLineTextField editBox = getRichTextField();
            boolean keepFormatting = Scribble.CONFIG_MANAGER.getConfig().copyFormattingCodes ^ event.hasShiftDown();

            RichText replacement = keepFormatting // Build replacement text
                ? RichText.fromFormattedString(clipboard)
                : new RichText(ChatFormatting.stripFormatting(clipboard), 
                            Optional.ofNullable(this.color).orElse(ChatFormatting.BLACK), 
                            this.modifiers);

            // Insert into current text
            MultilineTextField.StringView sel = editBox.getSelected();
            RichText current = editBox.getRichText();
            RichText full = (sel.beginIndex() != sel.endIndex()) 
                ? current.replace(sel.beginIndex(), sel.endIndex(), replacement)
                : current.insert(sel.beginIndex(), replacement);

            // Binary search splits text into chunks, trying to fill the 14 lines per page
            List<RichText> chunks = new ArrayList<>();
            int totalLen = full.getLength();
            int pos = 0;
            int lastChunkSize = -1;

            while (pos < totalLen && chunks.size() < 100) {
                int remaining = totalLen - pos;
                int lo, hi;
                // Narrow search range based on previous page size
                if (lastChunkSize > 0 && chunks.size() > 0) { // Pages 2+: Search around previous page size (±20%)
                    lo = Math.max(1, (int)(lastChunkSize * 0.8));
                    hi = Math.min(remaining, (int)(lastChunkSize * 1.2));
                } else {  // First page: Full search (might be partially filled)
                    lo = 0;
                    hi = remaining;
                }
                while (lo < hi) {
                    int mid = (lo + hi + 1) / 2;
                    int lines = this.font.getSplitter().splitLines(
                        full.subText(pos, pos + mid), editBox.width, Style.EMPTY
                    ).size();
                    if (lines <= editBox.lineLimit) lo = mid;
                    else hi = mid - 1;
                }

                int end = pos + Math.max(1, lo);
                chunks.add(full.subText(pos, end));
                lastChunkSize = lo;
                pos = end;
            }

            if (chunks.size() <= 1) {// Single page: Use standard edit command
                EditCommand cmd = new EditCommand(editBox, (box) -> box.insertText(clipboard));
                cmd.executeEdit(editBox);
                this.pushHistory(cmd);
                return true;
            }
            // Multi-page paste
            this.onCommandPush.accept(new me.chrr.scribble.history.command.PasteCommand(
                -1, current, chunks.get(0), chunks.subList(1, chunks.size()),
                chunks.get(chunks.size() - 1).getLength()
            ));
            return true;
        }

        // Respond to common hotkeys for toggling modifiers, such as Ctrl-B for bold.
        if (event.hasControlDown() && !event.hasShiftDown() && !event.hasAltDown()) {
            ChatFormatting modifier = switch (event.key()) {
                case GLFW.GLFW_KEY_B -> ChatFormatting.BOLD;
                case GLFW.GLFW_KEY_I -> ChatFormatting.ITALIC;
                case GLFW.GLFW_KEY_U -> ChatFormatting.UNDERLINE;
                case GLFW.GLFW_KEY_MINUS -> ChatFormatting.STRIKETHROUGH;
                case GLFW.GLFW_KEY_K -> ChatFormatting.OBFUSCATED;
                default -> null;
            };

            if (modifier != null) {
                this.applyFormatting(modifier, !this.modifiers.contains(modifier));
                return true;
            }
        }

        // Enter at end of full page -> new page after (only if overflow handling is enabled)
        if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) && onPageOverflow != null && onInsertPage != null) {
            if (textField.cursor() == getRichTextField().getRichText().getLength() && textField.getLineCount() >= getRichTextField().lineLimit) {
                // Enter should insert AFTER the current page, so pass `false` (false = insert after)
                onInsertPage.accept(false);
                return true;
            }
        }
        // Backspace on empty page -> delete and go back (only if overflow handling is enabled)
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE && onPageOverflow != null && onDeletePage != null && getRichTextField().getRichText().isEmpty()) {
            onDeletePage.accept(true);
            return true;
        }
        // Wrap the operation with an edit command if it edits the text.
        if (event.isCut() || event.isPaste() ||
                List.of(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER,
                        GLFW.GLFW_KEY_BACKSPACE, GLFW.GLFW_KEY_DELETE).contains(event.key())) {
            EditCommand command = new EditCommand(this.getRichTextField(),
                    (editBox) -> editBox.keyPressed(event));
            command.executeEdit(this.getRichTextField());
            this.pushHistory(command);
            return true;
        }


        return super.keyPressed(event);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        // Make sure the narrator narrates the plain text, not the formatting codes.
        narrationElementOutput.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.editBox",
                this.getMessage(), getRichTextField().getRichText().getPlainText()));
    }

    public RichMultiLineTextField getRichTextField() {
        return (RichMultiLineTextField) textField;
    }

    public static class Builder extends MultiLineEditBox.Builder {
        @Nullable
        private Runnable onInvalidateFormat = null;
        @Nullable
        private Consumer<EditCommand> onHistoryPush = null;
        @Nullable
        private Consumer<Command> onCommandPush = null;
        @Nullable
        private PageOverflowHandler onPageOverflow = null;
        @Nullable
        private Consumer<Boolean> onInsertPage = null;
        @Nullable
        private Consumer<Boolean> onDeletePage = null;

        public Builder onInvalidateFormat(Runnable onInvalidateFormat) {
            this.onInvalidateFormat = onInvalidateFormat;
            return this;
        }

        public Builder onHistoryPush(Consumer<EditCommand> onHistoryPush) {
            this.onHistoryPush = onHistoryPush;
            return this;
        }

        public Builder onCommandPush(Consumer<Command> onCommandPush) {
            this.onCommandPush = onCommandPush;
            return this;
        }

        public Builder onPageOverflow(PageOverflowHandler onPageOverflow) {
            this.onPageOverflow = onPageOverflow;
            return this;
        }

        public Builder onInsertPage(Consumer<Boolean> onInsertPage) {
            this.onInsertPage = onInsertPage;
            return this;
        }

        public Builder onDeletePage(Consumer<Boolean> onDeletePage) {
            this.onDeletePage = onDeletePage;
            return this;
        }

        @Override
        public @NotNull MultiLineEditBox build(Font font, int width, int height, Component message) {
            return new RichEditBoxWidget(font,
                    this.x, this.y, width, height,
                    this.placeholder, message, this.textColor,
                    this.textShadow, this.cursorColor, this.showBackground,
                    this.showDecorations, this.onInvalidateFormat, this.onHistoryPush,
                    this.onPageOverflow, this.onInsertPage, this.onDeletePage,
                    this.onCommandPush);
        }
    }
}
