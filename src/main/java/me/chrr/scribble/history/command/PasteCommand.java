package me.chrr.scribble.history.command;
import me.chrr.scribble.book.RichText;
import me.chrr.scribble.history.HistoryListener;
import java.util.List;

public class PasteCommand implements Command {
    public int page = -1;                            // Starting page (-1 = set by host before execution)
    private final RichText originalContent;          // Original text before paste, for undo
    private final RichText remainingContent;         // Text that fits on the first page
    private final List<RichText> overflowContents;   // Text that creates new pages, one item = one page
    private final int cursorPositionOnLastPage;      // Where cursor ends up after paste

    public PasteCommand(int page, RichText originalContent, RichText remainingContent, 
                        List<RichText> overflowContents, int cursorPositionOnLastPage) {
        this.page = page;
        this.originalContent = originalContent;
        this.remainingContent = remainingContent;
        this.overflowContents = overflowContents;
        this.cursorPositionOnLastPage = cursorPositionOnLastPage;
    }

    public int getOverflowCount() { //Returns how many new pages will be created 
        return overflowContents.size();
    }
    public List<RichText> getOverflowContents() { //Returns the list of overflow pages 
        return overflowContents;
    }

    public PasteCommand withTrimmedOverflow(int maxOverflow) { //This handles if the paste is larger than the space available in the book, then it pastes as much fits
        if (maxOverflow >= overflowContents.size()) return this;
        
        List<RichText> trimmed = overflowContents.subList(0, Math.max(0, maxOverflow));
        int cursorPos = trimmed.isEmpty() ? remainingContent.getLength() 
                                          : trimmed.get(trimmed.size() - 1).getLength();
        return new PasteCommand(page, originalContent, remainingContent, trimmed, cursorPos);
    }

    @Override //Execute the paste operation
    public void execute(HistoryListener listener) {
        var editBox = listener.scribble$history$getRichEditBox();
        int overflowCount = overflowContents.size();  
        
        listener.scribble$history$switchPage(page);
        editBox.setRichTextWithoutUpdating(remainingContent);
        editBox.onValueChange();
        
        for (int i = 0; i < overflowContents.size(); i++) {
            listener.scribble$history$insertPage(page + 1 + i, overflowContents.get(i));
        }
        
        listener.scribble$history$switchPage(page + overflowCount);
        editBox.cursor = editBox.selectCursor = cursorPositionOnLastPage;
    }

    @Override //Undo the paste operation:
    public void rollback(HistoryListener listener) {
        var editBox = listener.scribble$history$getRichEditBox();
        int overflowCount = overflowContents.size();
        
        // Remove inserted pages in reverse
        for (int i = overflowCount ; i >= 1; i--) {
            listener.scribble$history$deletePage(page + i);
        }
        
        listener.scribble$history$switchPage(page);
        editBox.setRichTextWithoutUpdating(originalContent);
        editBox.onValueChange();
        editBox.cursor = editBox.selectCursor = originalContent.getLength();
    }
}