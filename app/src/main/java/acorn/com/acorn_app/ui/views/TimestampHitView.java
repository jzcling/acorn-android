package acorn.com.acorn_app.ui.views;

import android.content.Context;
import android.support.v7.widget.AppCompatTextView;
import android.util.AttributeSet;

import com.algolia.instantsearch.ui.views.AlgoliaHitView;

import org.json.JSONObject;

import java.util.Date;

import acorn.com.acorn_app.utils.DateUtils;

public class TimestampHitView extends AppCompatTextView implements AlgoliaHitView {
    public TimestampHitView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onUpdateView(JSONObject result) {
        String timestamp = result.optString("pubDate");
        setText(DateUtils.parseDate(Long.parseLong(timestamp)));
    }
}