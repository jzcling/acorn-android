package acorn.com.acorn_app.ui.adapters;

import android.content.Context;
import android.support.annotation.NonNull;
import android.text.SpannableString;
import android.text.style.BackgroundColorSpan;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.firebase.ui.database.FirebaseRecyclerAdapter;
import com.firebase.ui.database.FirebaseRecyclerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.Comment;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;
import static acorn.com.acorn_app.ui.activities.CommentActivity.mSearchPos;
import static acorn.com.acorn_app.ui.activities.CommentActivity.mSearchText;
import static android.text.Spanned.SPAN_INCLUSIVE_INCLUSIVE;

public class CommentAdapter extends FirebaseRecyclerAdapter<Comment, CommentViewHolder> {
    private static final String TAG = "CommentAdapter";
    private final Context mContext;
    private final String mArticleId;

    public CommentAdapter(Context context, String articleId, FirebaseRecyclerOptions<Comment> options) {
        super(options);
        mContext = context;
        mArticleId = articleId;
    }

    @NonNull
    @Override
    public CommentViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case 0:
                return new CommentViewHolder(mContext, inflater.inflate(R.layout.item_comment_self, parent, false));
            case 1:
            default:
                return new CommentViewHolder(mContext, inflater.inflate(R.layout.item_comment, parent, false));
        }
    }

    @Override
    protected void onBindViewHolder(@NonNull CommentViewHolder holder, int position, @NonNull Comment comment) {
        holder.bind(mArticleId, comment);
    }

    @Override
    public int getItemViewType(int position) {
        if (getItem(position).getUid().equals(mUid)) {
            return 0;
        }
        return 1;
    }

    public List<Integer> findText(String searchText) {
        List<Integer> positions = new ArrayList<>();
        for (int i = getItemCount() - 1; i >= 0; i--) {
            String text = getItem(i).getCommentText();
            if (text.contains(searchText)) {
                positions.add(i);
            }
        }
        return positions;
    }

    @Override
    public void onViewAttachedToWindow(@NonNull CommentViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        int adapterPos = holder.getAdapterPosition();
        if (mSearchPos != null && mSearchPos.contains(adapterPos)) {
            String originalText = holder.commentTextView.getText().toString();
            SpannableString highlightedText = new SpannableString(originalText);
            Pattern p = Pattern.compile(mSearchText, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(originalText);
            while (m.find()) {
                highlightedText.setSpan(new BackgroundColorSpan(
                        mContext.getResources().getColor(R.color.search_comment_highlight)),
                        m.start(), m.end(), SPAN_INCLUSIVE_INCLUSIVE);
            }
            holder.commentTextView.setText(highlightedText);
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull CommentViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        String originalText = holder.commentTextView.getText().toString();
        holder.commentTextView.setText(originalText);
    }
}
