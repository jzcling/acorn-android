package acorn.com.acorn_app.ui.adapters;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
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
import acorn.com.acorn_app.models.Article;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mQuery;

public class ArticleAdapter extends RecyclerView.Adapter<ArticleViewHolder> {
    private static final String TAG = "ArticleAdapter";
    private final Context mContext;
    private String mArticleType;
    private final OnLongClickListener longClickListener;
    private List<Article> mArticleList = new ArrayList<>();

    private final Map<DatabaseReference, ValueEventListener> mRefObservedList = new HashMap<>();

    public ArticleAdapter(final Context context,
                          @Nullable OnLongClickListener longClickListener) {
        mContext = context;
        this.longClickListener = longClickListener;
    }

    @Override
    public int getItemViewType(int position) {
        Article article = mArticleList.get(position);
        if (article.getType() == null || article.getType().equals("article")) {
            return 0;
        } else {
            return 1;
        }
    }

    @NonNull
    @Override
    public ArticleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        mArticleType = viewType == 0 ? "article" : "post";
        LayoutInflater inflater = LayoutInflater.from(mContext);
        if (mArticleType.equals("post")) {
            view = inflater.inflate(R.layout.item_post_card, parent, false);
        } else {
            view = inflater.inflate(R.layout.item_article_card, parent, false);
        }
        return new ArticleViewHolder(mContext, view, mArticleType, longClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ArticleViewHolder holder, int position) {
        Article article = mArticleList.get(position);
        holder.bind(article);
    }

    @Override
    public int getItemCount() {
        return mArticleList.size();
    }

    @Override
    public void onViewAttachedToWindow(@NonNull ArticleViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        if (mQuery.state == 3 || mQuery.state == 4) {
            int adapterPos = holder.getAdapterPosition();
            Article article = mArticleList.get(adapterPos);
            DatabaseReference articleRef = FirebaseDatabase.getInstance()
                    .getReference("article/" + article.getObjectID());
            ValueEventListener searchArticleListener = articleRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.getValue() != null) {
                        Article snapArticle = dataSnapshot.getValue(Article.class);
                        holder.bind(snapArticle);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError databaseError) { }
            });
            mRefObservedList.put(articleRef, searchArticleListener);

        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ArticleViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        if (mQuery.state == 3 || mQuery.state == 4) {
            Article article;
            if (getItemCount() > 0 && holder.getAdapterPosition() != -1) {
                article = mArticleList.get(holder.getAdapterPosition());
                DatabaseReference articleRef = FirebaseDatabase.getInstance()
                        .getReference("article/" + article.getObjectID());
                ValueEventListener searchArticleListener = mRefObservedList.get(articleRef);
                if (searchArticleListener != null) {
                    articleRef.removeEventListener(searchArticleListener);
                }
                mRefObservedList.remove(articleRef);

            }
        }
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

    public List<Article> getList() {
        return mArticleList;
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
