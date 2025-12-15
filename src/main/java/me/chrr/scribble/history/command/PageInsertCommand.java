package me.chrr.scribble.history.command;

import me.chrr.scribble.history.HistoryListener;

public class PageInsertCommand implements Command {
    private final int page;
    private final boolean insertedAfter;

    public PageInsertCommand(int page, boolean insertedAfter) {
        this.page = page;
        this.insertedAfter = insertedAfter;
    }

    @Override
    public void execute(HistoryListener listener) {
        listener.scribble$history$insertPage(page, null);
    }

    @Override
    public void rollback(HistoryListener listener) {
        listener.scribble$history$deletePage(page);
        // If inserted after (Enter key), go back to the original page
        if (insertedAfter && page > 0) {
            listener.scribble$history$switchPage(page - 1);
        }
    }
}
