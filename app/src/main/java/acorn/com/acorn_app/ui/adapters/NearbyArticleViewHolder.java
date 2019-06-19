package acorn.com.acorn_app.ui.adapters;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.DateUtils;

public class NearbyArticleViewHolder extends RecyclerView.ViewHolder {
    private static final String TAG = "NearbyArticleViewHolder";
    private final String mArticleType;
    private final Context mContext;

    private final TextView title;
    private final TextView contributor;
    private TextView separator;
    private TextView pubDate;
    private final ImageView netVote;
    private final TextView voteCount;
    private final TextView commentCount;
    private final ImageView mainImage;

    private ConstraintLayout rootView;
    private ConstraintLayout viewBackground;
    public ConstraintLayout viewForeground;

    private final AppExecutors mExecutors = AppExecutors.getInstance();

    public NearbyArticleViewHolder(Context context, View view, String articleType) {
        super(view);
        mContext = context;
        mArticleType = articleType;

        title = (TextView) view.findViewById(R.id.card_title);
        contributor = (TextView) view.findViewById(R.id.card_contributor);
        separator = (TextView) view.findViewById(R.id.card_sub_separator);
        pubDate = (TextView) view.findViewById(R.id.card_date);
        netVote = (ImageView) view.findViewById(R.id.card_image_net_vote);
        voteCount = (TextView) view.findViewById(R.id.card_vote_count);
        commentCount = (TextView) view.findViewById(R.id.card_comment_count);
        mainImage = (ImageView) view.findViewById(R.id.card_image);

        rootView = (ConstraintLayout) view.findViewById(R.id.card_root);
        viewBackground = (ConstraintLayout) view.findViewById(R.id.view_background);
        viewForeground = (ConstraintLayout) view.findViewById(R.id.view_foreground);
    }

    private ArticleOnClickListener onClickListener(Article article, String cardAttribute) {
        return new ArticleOnClickListener(mContext, article, cardAttribute);
    }

    public void bind(Article article) {
        title.setText(article.getTitle());
        if (article.getSource() != null && !article.getSource().equals(""))
            contributor.setText(article.getSource().length() > 20 ?
                    article.getSource().substring(0, 17) + "..." : article.getSource());
        if (mArticleType.equals("article")) {
            pubDate.setText(DateUtils.parseDate(article.getPubDate()));
        } else if (pubDate != null) {
            pubDate.setText(DateUtils.parseDate(article.getPostDate()));
        }
        if (article.getVoteCount() < 0) {
            netVote.setImageResource(R.drawable.ic_arrow_down);
            netVote.setColorFilter(mContext.getColor(R.color.card_down_arrow_tint));
        } else {
            netVote.setImageResource(R.drawable.ic_arrow_up);
            netVote.setColorFilter(mContext.getColor(R.color.card_up_arrow_tint));
        }
        voteCount.setText(String.valueOf(article.getVoteCount() == null ? 0 : article.getVoteCount()));
        commentCount.setText(String.valueOf(article.getCommentCount() == null ? 0 : article.getCommentCount()));

        if (!(article.getImageUrl() == null || article.getImageUrl().equals(""))) {
            Object imageUrl;
            if (article.getImageUrl().startsWith("gs://")) {
                imageUrl = FirebaseStorage.getInstance()
                        .getReferenceFromUrl(article.getImageUrl());
            } else {
                imageUrl = article.getImageUrl();
            }
            mainImage.setVisibility(View.VISIBLE);
            Glide.with(mContext.getApplicationContext())
                    .load(imageUrl)
                    .into(mainImage);

            if (mArticleType.equals("post")) {
                if (article.getSource() == null || article.getSource().equals("")) {
                    contributor.setText(article.getPostAuthor());
                    title.setText(article.getPostText().length() > 200 ?
                            article.getPostText().substring(0, 197) + "..." :
                            article.getPostText());
                } else if (article.getTitle() != null && !article.getTitle().equals("")) {
                    separator.setVisibility(View.GONE);
                    pubDate.setVisibility(View.GONE);
                }
            }
        } else if (!(article.getPostImageUrl() == null || article.getPostImageUrl().equals(""))) {
            StorageReference storageReference = FirebaseStorage.getInstance()
                    .getReferenceFromUrl(article.getPostImageUrl());
            title.setText(article.getPostText());
            contributor.setText(article.getPostAuthor());
            Glide.with(mContext.getApplicationContext())
                    .load(storageReference)
                    .apply(new RequestOptions()
                            .placeholder(R.drawable.loading_spinner))
                    .into(mainImage);
        } else {
            if (mArticleType.equals("post")) {
                title.setText(article.getPostText());
                contributor.setText(article.getPostAuthor());
            }
            mainImage.setVisibility(View.GONE);
        }

        if (article.getLink() != null && !article.getLink().equals("")) {
            rootView.setOnClickListener(onClickListener(article, "title"));
        } else {
            rootView.setOnClickListener(onClickListener(article, "comment"));
        }
        title.setClickable(false);
        mainImage.setClickable(false);
    }
}
