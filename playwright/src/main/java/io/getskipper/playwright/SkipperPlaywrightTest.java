package io.getskipper.playwright;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Convenience base class for Playwright tests gated by Skipper.
 *
 * <p>Extends this class to get:
 * <ul>
 *   <li>{@link #page} — a fresh {@link Page} for each test method</li>
 *   <li>{@link #context} — the shared {@link BrowserContext} for the test class</li>
 *   <li>Automatic test gating via the Skipper spreadsheet</li>
 * </ul>
 *
 * <p>Example:
 * <pre>{@code
 * class LoginTest extends SkipperPlaywrightTest {
 *     @Test
 *     void shouldLogin() {
 *         page.navigate("https://example.com/login");
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * <p>Configure Skipper via environment variables (see {@link io.getskipper.core.SkipperConfig#fromEnvironment()}).
 */
@ExtendWith(SkipperPlaywrightExtension.class)
public abstract class SkipperPlaywrightTest {

    /** The shared browser context for this test class. */
    protected BrowserContext context;

    /** A fresh page opened before each test method and closed after. */
    protected Page page;

    @BeforeEach
    void openPage(ExtensionContext extensionContext) {
        context = SkipperPlaywrightExtension.getBrowserContext(extensionContext);
        page = context.newPage();
    }

    @AfterEach
    void closePage() {
        if (page != null) {
            page.close();
            page = null;
        }
    }
}
