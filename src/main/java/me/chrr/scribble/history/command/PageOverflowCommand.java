package me.chrr.scribble.history.command;
import me.chrr.scribble.book.RichText;
import me.chrr.scribble.history.HistoryListener;

public class PageOverflowCommand implements Command {
    private final int sourcePage;
    private final RichText originalContent;
    private final RichText remainingContent;
    private final RichText overflowContent;
    private final int cursorPositionOnNewPage;

    public PageOverflowCommand(int sourcePage, RichText originalContent, RichText remainingContent, 
                               RichText overflowContent, int cursorPositionOnNewPage) {
        this.sourcePage = sourcePage;
        this.originalContent = originalContent;
        this.remainingContent = remainingContent;
        this.overflowContent = overflowContent;
        this.cursorPositionOnNewPage = cursorPositionOnNewPage;
    }

    @Override
    public void execute(HistoryListener listener) {
        listener.scribble$history$switchPage(sourcePage);
        listener.scribble$history$getRichEditBox().setRichTextWithoutUpdating(remainingContent);
        listener.scribble$history$getRichEditBox().onValueChange();
        
        // Insert new page with overflow content
        listener.scribble$history$insertPage(sourcePage + 1, overflowContent);
        
        // Ensure we're on the new page and set cursor position
        listener.scribble$history$switchPage(sourcePage + 1);
        listener.scribble$history$getRichEditBox().cursor = cursorPositionOnNewPage;
        listener.scribble$history$getRichEditBox().selectCursor = cursorPositionOnNewPage;
    }

    @Override
    public void rollback(HistoryListener listener) {
        // Delete the page
        listener.scribble$history$deletePage(sourcePage + 1);
        listener.scribble$history$switchPage(sourcePage);
        
        // Restore the exact original content before overflow
        listener.scribble$history$getRichEditBox().setRichTextWithoutUpdating(originalContent);
        listener.scribble$history$getRichEditBox().onValueChange();
        
        // Set cursor at the end of the original content (where the overflow was triggered)
        listener.scribble$history$getRichEditBox().cursor = originalContent.getLength();
        listener.scribble$history$getRichEditBox().selectCursor = originalContent.getLength();
    }
}