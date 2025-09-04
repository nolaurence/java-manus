package cn.nolaurene.cms.service.sandbox.worker.mcp.snapshot;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import java.util.List;

public class PageSnapshot {

    private final Page page;
    private String text;

    public PageSnapshot(Page page) {
        this.page = page;
    }

    public static PageSnapshot create(Page page) {
        PageSnapshot snapshot = new PageSnapshot(page);
        snapshot.build();
        return snapshot;
    }

    public Locator refLocator(String element, String ref) {
        return page.locator("aria-ref=" + ref).describe(element);
    }

    public String text() {
        return text;
    }

    private void build() {
        PlaywrightAriaSnapshot playwrightAriaSnapshot = new PlaywrightAriaSnapshot(page);
        String snapshot = playwrightAriaSnapshot.snapshotForAIWhenStable();
        this.text = String.join("\n", List.of("`- Page Snapshot", "```yaml", snapshot, "```"));
    }
}
