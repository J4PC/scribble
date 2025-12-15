package me.chrr.scribble.history.command;
import me.chrr.scribble.history.HistoryListener;

public class PageInsertCommand implements Command {
    private final int basePage;
    private final boolean insertBefore; // insertBefore = true => insert to the LEFT (before) insertBefore = false => insert to the RIGHT (after) 

    public PageInsertCommand(int basePage, boolean insertBefore) {
        this.basePage = basePage;
        this.insertBefore = insertBefore;
    }
    @Override
    public void execute(HistoryListener listener) {
        int insertAt = insertBefore ? Math.max(0, basePage) : basePage + 1; 
        listener.scribble$history$insertPage(insertAt, null);
    }//There is probably a better way to do this because the insert page function was originally designed to just insert before. But this works the same; it's just not elegant

    @Override
    public void rollback(HistoryListener listener) {
        int insertAt = insertBefore ? Math.max(0, basePage - 1) : basePage + 1;
        listener.scribble$history$deletePage(insertAt);
        if (!insertBefore) { // If the insertion was performed after the base page (Enter behavior), the view needs to switch back to the left page and not the right, which is the default behavior.
            listener.scribble$history$switchPage(basePage);
        }
    }
}
