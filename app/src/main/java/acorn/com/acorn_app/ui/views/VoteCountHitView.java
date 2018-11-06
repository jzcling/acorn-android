package acorn.com.acorn_app.ui.views;

import android.content.Context;
import android.support.v7.widget.AppCompatImageView;
import android.util.AttributeSet;

import com.algolia.instantsearch.ui.views.AlgoliaHitView;

import org.json.JSONObject;

import acorn.com.acorn_app.R;

public class VoteCountHitView extends AppCompatImageView implements AlgoliaHitView {
    public VoteCountHitView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onUpdateView(JSONObject result) {
        Integer voteCount = result.optInt("voteCount", 0);
        if (voteCount < 0) {
            setImageResource(R.drawable.ic_arrow_down);
            setColorFilter(getContext().getColor(R.color.card_down_arrow_tint));
        } else {
            setImageResource(R.drawable.ic_arrow_up);
            setColorFilter(getContext().getColor(R.color.card_up_arrow_tint));
        }
    }
}