package acorn.com.acorn_app.providers;

import android.content.SearchRecentSuggestionsProvider;

public class SearchProvider extends SearchRecentSuggestionsProvider {
    public final static String AUTHORITY = "acorn.com.acorn_app.com.acorn_app.providers.SearchProvider";
    public final static int MODE = DATABASE_MODE_QUERIES;

    public SearchProvider() {
        setupSuggestions(AUTHORITY, MODE);
    }
}
