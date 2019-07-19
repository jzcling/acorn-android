package acorn.com.acorn_app.ui.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import acorn.com.acorn_app.utils.ShareUtils;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import android.text.Html;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.DateUtils;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;
import static acorn.com.acorn_app.utils.UiUtils.createToast;
import static acorn.com.acorn_app.utils.UiUtils.increaseTouchArea;
import static android.content.Context.CLIPBOARD_SERVICE;

public class ArticleViewHolder extends RecyclerView.ViewHolder {
    private static final String TAG = "ArticleViewHolder";
    private final String mArticleType;
    private final Context mContext;
    private final ArticleAdapter.OnLongClickListener longClickListener;

    private final TextView title;
    private final TextView contributor;
    private TextView separator = null;
    private TextView pubDate = null;
    private final ImageView netVote;
    private final TextView voteCount;
    private ConstraintLayout commentFrame;
    private final TextView commentCount;
    private final ImageView mainImage;
    private final ImageView mainImageCard;
    private ImageView postImage;

    private TextView theme;
    private TextView readTime;
    private TextView topSeparator;
    private CheckBox upVoteView;
    private CheckBox downVoteView;
    private CheckBox commentView;
    public CheckBox favView;
    private CheckBox shareView;
    private ImageButton optionsButton;

    private TextView postAuthor;
    private TextView postDate;
    private TextView postText;
    private ImageView postExpand;
    private CardView cardArticle;
    private boolean isExpanded = false;
    
    private RecyclerView duplicatesRv;
    private LinearLayoutManager llm;
    private DuplicateArticleAdapter adapter;

    private NetworkDataSource mDataSource;
    private final AppExecutors mExecutors = AppExecutors.getInstance();

    public ArticleViewHolder(Context context, View view, String articleType,
                             ArticleAdapter.OnLongClickListener longClickListener) {
        super(view);
        mContext = context;
        mArticleType = articleType;
        this.longClickListener = longClickListener;

        mDataSource = NetworkDataSource.getInstance(mContext, mExecutors);

        title = (TextView) view.findViewById(R.id.card_title);
        contributor = (TextView) view.findViewById(R.id.card_contributor);
        separator = (TextView) view.findViewById(R.id.card_sub_separator);
        pubDate = (TextView) view.findViewById(R.id.card_date);
        netVote = (ImageView) view.findViewById(R.id.card_image_net_vote);
        voteCount = (TextView) view.findViewById(R.id.card_vote_count);
        if (mArticleType.equals("article"))
            commentFrame = (ConstraintLayout) view.findViewById(R.id.card_comment_frame);
        commentCount = (TextView) view.findViewById(R.id.card_comment_count);
        mainImage = (ImageView) view.findViewById(R.id.card_image);
        mainImageCard = (ImageView) view.findViewById(R.id.card_image_card);

        if (mArticleType.equals("article")) {
            readTime = (TextView) view.findViewById(R.id.card_read_time);
            topSeparator = (TextView) view.findViewById(R.id.card_top_separator);
        } else {
            postAuthor = (TextView) view.findViewById(R.id.post_author);
            postDate = (TextView) view.findViewById(R.id.post_date);
            postText = (TextView) view.findViewById(R.id.post_text);
            postExpand = (ImageView) view.findViewById(R.id.post_expand);
            postImage = (ImageView) view.findViewById(R.id.post_image);
            cardArticle = (CardView) view.findViewById(R.id.card_article);

            optionsButton = (ImageButton) view.findViewById(R.id.card_button_options);
        }
        theme = (TextView) view.findViewById(R.id.card_theme);

        upVoteView = (CheckBox) view.findViewById(R.id.card_button_upvote);
        downVoteView = (CheckBox) view.findViewById(R.id.card_button_downvote);
        commentView = (CheckBox) view.findViewById(R.id.card_button_comment);
        favView = (CheckBox) view.findViewById(R.id.card_button_favourite);
        shareView = (CheckBox) view.findViewById(R.id.card_button_share);

        duplicatesRv = (RecyclerView) view.findViewById(R.id.card_duplicates_rv);
        llm = new LinearLayoutManager(mContext);
        llm.setOrientation(RecyclerView.HORIZONTAL);
        adapter = new DuplicateArticleAdapter(mContext);
    }

    private ArticleOnClickListener onClickListener(Article article, String cardAttribute) {
        return new ArticleOnClickListener(mContext, article, cardAttribute,
                upVoteView, downVoteView, commentView, favView, shareView);
    }

    private ArticleOnLongClickListener onLongClickListener(Article article) {
        return new ArticleOnLongClickListener(article, longClickListener);
    }

    public class ArticleOnLongClickListener implements View.OnLongClickListener {
        private final Article article;
        private final ArticleAdapter.OnLongClickListener longClickListener;

        public ArticleOnLongClickListener(Article article,
                                          ArticleAdapter.OnLongClickListener longClickListener) {
            this.article = article;
            this.longClickListener = longClickListener;
        }

        @Override
        public boolean onLongClick(View v) {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            int id = v.getId();
            String text = ((TextView) v).getText().toString();
            longClickListener.onLongClick(article, id, text);
            return true;
        }
    }

    public void bind(Article article) {
        if (mContext != null) {
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
                if (mainImageCard != null) {
                    mainImageCard.setVisibility(View.VISIBLE);
                }
                mainImage.setVisibility(View.VISIBLE);
                Glide.with(mContext.getApplicationContext())
                        .load(imageUrl)
                        .into(mainImage);

                if (mArticleType.equals("post")) {
                    cardArticle.setVisibility(View.VISIBLE);
                    if (article.getSource() == null || article.getSource().equals(""))
                        contributor.setVisibility(View.GONE);
                    postImage.setVisibility(View.GONE);
                }

            } else if (!(article.getPostImageUrl() == null || article.getPostImageUrl().equals(""))) {
                StorageReference storageReference = FirebaseStorage.getInstance()
                        .getReferenceFromUrl(article.getPostImageUrl());
                if (mainImageCard != null) {
                    mainImageCard.setVisibility(View.GONE);
                } else {
                    mainImage.setVisibility(View.GONE);
                }
                cardArticle.setVisibility(View.GONE);
                postImage.setVisibility(View.VISIBLE);
                Glide.with(mContext.getApplicationContext())
                        .load(storageReference)
                        .apply(new RequestOptions()
                                .placeholder(R.drawable.loading_spinner))
                        .into(postImage);
            } else {
                if (mArticleType.equals("post")) {
                    if (article.getTitle() != null && !article.getTitle().equals("")) {
                        cardArticle.setVisibility(View.VISIBLE);
                    } else {
                        cardArticle.setVisibility(View.GONE);
                    }
                    postImage.setVisibility(View.GONE);
                }
                if (mainImageCard != null) {
                    mainImageCard.setVisibility(View.GONE);
                } else {
                    mainImage.setVisibility(View.GONE);
                }
            }

            if (mArticleType.equals("article")) {
                title.setOnClickListener(onClickListener(article, "title"));
                title.setOnLongClickListener(v -> {
                    String shareUri = ShareUtils.createShareUri(article.getObjectID(), article.getLink(), mUid);
                    ShareUtils.createShortDynamicLink(shareUri, (dynamicLink) -> {
                        ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("simple text", dynamicLink);
                        clipboard.setPrimaryClip(clip);
                        createToast(mContext, "Link copied", 1000);
                    });
                    return true;
                });
                contributor.setOnClickListener(v -> {
                    createToast(mContext, "Hold to filter by source", 1000);
                    contributor.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                });
                contributor.setOnLongClickListener(onLongClickListener(article));
                mainImage.setOnClickListener(onClickListener(article, "title"));
            } else {
                if (article.getLink() != null) {
                    cardArticle.setOnClickListener(onClickListener(article, "title"));
                    cardArticle.setOnLongClickListener(v -> {
                        String shareUri = ShareUtils.createShareUri(article.getObjectID(), article.getLink(), mUid);
                        ShareUtils.createShortDynamicLink(shareUri, (dynamicLink) -> {
                            ClipboardManager clipboard = (ClipboardManager) mContext.getSystemService(CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("simple text", dynamicLink);
                            clipboard.setPrimaryClip(clip);
                            createToast(mContext, "Link copied", 1000);
                        });
                        return true;
                    });
                } else {
                    postImage.setOnClickListener(onClickListener(article, "postImage"));
                    cardArticle.setOnClickListener(onClickListener(article, "postImage"));
                }

                optionsButton.setVisibility(View.VISIBLE);
                optionsButton.setOnClickListener(v -> {
                    PopupMenu popup = new PopupMenu(mContext, optionsButton);
                    popup.inflate(R.menu.card_options_menu);
                    popup.setOnMenuItemClickListener(item -> {
                        switch (item.getItemId()) {
                            case R.id.action_report_post:
                                mExecutors.networkIO().execute(() -> {
                                    mDataSource.reportPost(article);
                                });
                                return true;
                            default:
                                return false;
                        }
                    });
                    popup.show();
                });
            }
            theme.setText(article.getMainTheme());
            theme.setOnClickListener(v -> {
                createToast(mContext, "Hold to filter by theme", 1000);
                theme.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
            });
            theme.setOnLongClickListener(onLongClickListener(article));
            if (mArticleType.equals("article")) {
                if (article.getReadTime() != null) {
                    topSeparator.setVisibility(View.VISIBLE);
                    readTime.setVisibility(View.VISIBLE);
                    readTime.setText(article.getReadTime() + "m read");
                } else {
                    topSeparator.setVisibility(View.GONE);
                    readTime.setVisibility(View.GONE);
                }
            } else {
                postAuthor.setText(article.getPostAuthor().length() > 20 ?
                        article.getPostAuthor().substring(0, 17) + "..." : article.getPostAuthor());
                postText.setText(article.getPostText().length() > 200 ?
                        article.getPostText().substring(0, 197) + "..." : article.getPostText());
                if (article.getPostText().length() > 200) {
                    postExpand.setVisibility(View.VISIBLE);
                    increaseTouchArea(postExpand);
                    postExpand.setOnClickListener(v -> {
                        if (isExpanded) {
                            postExpand.setBackground(mContext.getDrawable(R.drawable.chevron_down));
                            postText.setText(article.getPostText().substring(0, 197) + "...");
                            isExpanded = false;
                        } else {
                            postExpand.setBackground(mContext.getDrawable(R.drawable.chevron_up));
                            postText.setText(article.getPostText());
                            isExpanded = true;
                        }
                    });
                } else {
                    postExpand.setVisibility(View.GONE);
                }
                postDate.setText(DateUtils.parseDate(article.getPostDate()));

                if (article.getTitle() == null || article.getTitle().equals(""))
                    cardArticle.setVisibility(View.GONE);
            }

            increaseTouchArea(upVoteView);
            increaseTouchArea(downVoteView);
            increaseTouchArea(commentView);
            increaseTouchArea(favView);
            increaseTouchArea(shareView);

            upVoteView.setOnClickListener(onClickListener(article, "upvote"));
            downVoteView.setOnClickListener(onClickListener(article, "downvote"));
            if (mArticleType.equals("article"))
                commentFrame.setOnClickListener(onClickListener(article, "comment"));
            commentView.setOnClickListener(onClickListener(article, "comment"));
            favView.setOnClickListener(onClickListener(article, "favourite"));
            shareView.setOnClickListener(onClickListener(article, "share"));

            if (article.upvoters.containsKey(mUid)) {
                upVoteView.setChecked(true);
            } else {
                upVoteView.setChecked(false);
            }
            if (article.downvoters.containsKey(mUid)) {
                downVoteView.setChecked(true);
            } else {
                downVoteView.setChecked(false);
            }
            if (article.commenters.containsKey(mUid)) {
                commentView.setChecked(true);
            } else {
                commentView.setChecked(false);
            }
            if (article.savers.containsKey(mUid)) {
                favView.setChecked(true);
            } else {
                favView.setChecked(false);
            }

            // replace links in title with hyperlinks
            if (postText != null) {
                String tempPostText = postText.getText().toString();

                Pattern urlPattern = Pattern.compile("((?:https?://|www\\.)[a-zA-Z0-9+&@#/%=~_|$?!:,.-]*\\b)");
                Matcher m = urlPattern.matcher(tempPostText);
                if (m.find()) {
                    String truncatedLink = m.group(1).length() > 40 ?
                            m.group(1).substring(0, 37) + "..." : m.group(1);
                    tempPostText = m.replaceAll("<a href=\"$1\">" + truncatedLink + "</a>");

                    postText.setText(Html.fromHtml(tempPostText));
                }
            }
            
            // check for duplicates
            if (duplicatesRv != null) {
                if (article.duplicates.size() > 0) {
                    duplicatesRv.setLayoutManager(llm);
                    duplicatesRv.setAdapter(adapter);
                    ((SimpleItemAnimator) duplicatesRv.getItemAnimator()).setSupportsChangeAnimations(false);
                    List<String> duplicateIds = new ArrayList<>(article.duplicates.keySet());
                    mDataSource.getDuplicateArticles(duplicateIds, (articles -> {
                        adapter.setList(articles);
                        duplicatesRv.setVisibility(View.VISIBLE);
                    }));
                } else {
                    duplicatesRv.setVisibility(View.GONE);
                }
            }
        }
    }
}
