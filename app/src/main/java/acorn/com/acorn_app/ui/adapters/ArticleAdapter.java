package acorn.com.acorn_app.ui.adapters;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.UiUtils;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mQuery;

public class ArticleAdapter extends RecyclerView.Adapter<ArticleViewHolder> {
    private static final String TAG = "ArticleAdapter";
    private final Context mContext;
    private String mYoutubeApiKey;
    private String mArticleType;
    private final OnLongClickListener longClickListener;
    private List<Article> mArticleList = new ArrayList<>();

    //Data source
    private NetworkDataSource mDataSource;
    private final AppExecutors mExecutors = AppExecutors.getInstance();

    private final Map<DatabaseReference, ValueEventListener> mRefObservedList = new HashMap<>();

    public ArticleAdapter(final Context context,
                          @Nullable OnLongClickListener longClickListener) {
        mContext = context;
        this.longClickListener = longClickListener;
        mDataSource = NetworkDataSource.getInstance(mContext, mExecutors);
        mDataSource.getYoutubeApiKey((apiKey) -> mYoutubeApiKey = apiKey);
    }

    @Override
    public int getItemViewType(int position) {
        Article article = mArticleList.get(position);
        if (article.getType() == null || article.getType().equals("article")) {
            if (article.duplicates.size() > 0) {
                return 1;
            } else {
                return 0;
            }
        } else if (article.getType().equals("post")) {
            return 2;
        } else if (article.getType().equals("video")) {
            return 3;
        } else {
            return 0;
        }
    }

    @NonNull
    @Override
    public ArticleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        LayoutInflater inflater = LayoutInflater.from(mContext);
        if (viewType == 0) {
            mArticleType = "article";
            view = inflater.inflate(R.layout.item_article_card, parent, false);
        } else if (viewType == 1) {
            mArticleType = "article";
            view = inflater.inflate(R.layout.item_article_card_with_duplicates, parent, false);
        } else if (viewType == 2) {
            mArticleType = "post";
            view = inflater.inflate(R.layout.item_post_card, parent, false);
        } else if (viewType == 3) {
            mArticleType = "video";
            view = inflater.inflate(R.layout.item_video_card, parent, false);
        } else {
            mArticleType = "article";
            view = inflater.inflate(R.layout.item_article_card, parent, false);
        }
        return new ArticleViewHolder(mContext, view, mArticleType, mYoutubeApiKey, longClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ArticleViewHolder holder, int position) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        Article article = mArticleList.get(position);
        if (article.getType().equals("video")) {
            holder.bindVideo(article);
        } else {
            holder.bind(article);
        }

        if (position == 0) {
            if (!sharedPrefs.getBoolean(mContext.getString(R.string.helper_saved_seen), false)) {
                View target = holder.favView;

                String title = "Save";
                String text = "Too busy to read? Save articles for later! You can even get reminder " +
                        "notifications for events or deals a day before they happen!";
                UiUtils.highlightView(mContext, target, title, text);

                sharedPrefs.edit().putBoolean(mContext.getString(R.string.helper_saved_seen), true).apply();
            }
        }
    }

    @Override
    public int getItemCount() {
        return mArticleList.size();
    }

    public void setList(List<Article> newList, Runnable onComplete) {
        mArticleList = new ArrayList<>(newList);
        notifyDataSetChanged();
        onComplete.run();
    }

    public void setList(List<Article> newList) {
        mArticleList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    public void appendList(List<Article> addList) {
        mArticleList.addAll(addList);
        notifyDataSetChanged();
    }

    public List<Article> getList() {
        return mArticleList;
    }

    public List<String> getIdList() {
        List<String> idList = new ArrayList<>();
        for (Article article : mArticleList) {
            idList.add(article.getObjectID());
        }
        return idList;
    }

    public Article getLastItem() {
        return mArticleList.get(mArticleList.size()-1);
    }

    public void clear() {
        mArticleList.clear();
        if (mRefObservedList.size() > 0) {
            for (DatabaseReference ref : mRefObservedList.keySet()) {
                ref.removeEventListener(mRefObservedList.get(ref));
            }
            mRefObservedList.clear();
        }
    }

    public interface OnLongClickListener {
        void onLongClick(Article article, int id, String text);
    }
}
