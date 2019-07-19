package acorn.com.acorn_app.ui.adapters;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.Html;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.DateUtils;
import acorn.com.acorn_app.utils.ShareUtils;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;
import static acorn.com.acorn_app.utils.UiUtils.createToast;
import static acorn.com.acorn_app.utils.UiUtils.increaseTouchArea;
import static android.content.Context.CLIPBOARD_SERVICE;

public class DuplicateArticleViewHolder extends RecyclerView.ViewHolder {
    private static final String TAG = "DuplicateArticleViewHolder";
    private final Context mContext;

    private final TextView title;
    private final TextView contributor;
    private final TextView pubDate;
    private final ImageView mainImage;
    private final CardView mainImageCard;

    public DuplicateArticleViewHolder(Context context, View view) {
        super(view);
        mContext = context;

        title = (TextView) view.findViewById(R.id.card_title);
        contributor = (TextView) view.findViewById(R.id.card_contributor);
        pubDate = (TextView) view.findViewById(R.id.card_date);
        mainImage = (ImageView) view.findViewById(R.id.card_image);
        mainImageCard = (CardView) view.findViewById(R.id.card_image_card);
    }

    private ArticleOnClickListener onClickListener(Article article, String cardAttribute) {
        return new ArticleOnClickListener(mContext, article, cardAttribute);
    }

    public void bind(Article article) {
        if (mContext != null) {
            DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
            int width = (int) Math.floor(0.5f * displayMetrics.widthPixels);
            title.setWidth(width);
            String titleText = article.getTitle().length() > 70 ?
                    article.getTitle().substring(0, 67) + "..." : article.getTitle();
            title.setText(titleText);
            if (article.getSource() != null && !article.getSource().equals(""))
                contributor.setText(article.getSource().length() > 20 ?
                        article.getSource().substring(0, 17) + "..." : article.getSource());
            pubDate.setText(DateUtils.parseDate(article.getPubDate()));

            if (!(article.getImageUrl() == null || article.getImageUrl().equals(""))) {
                Object imageUrl;
                if (article.getImageUrl().startsWith("gs://")) {
                    imageUrl = FirebaseStorage.getInstance()
                            .getReferenceFromUrl(article.getImageUrl());
                } else {
                    imageUrl = article.getImageUrl();
                }
                mainImageCard.setVisibility(View.VISIBLE);
                Glide.with(mContext.getApplicationContext())
                        .load(imageUrl)
                        .into(mainImage);
            } else {
                mainImageCard.setVisibility(View.GONE);
            }

            title.setOnClickListener(onClickListener(article, "title"));
            mainImage.setOnClickListener(onClickListener(article, "title"));
        }
    }
}
