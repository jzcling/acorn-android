package acorn.com.acorn_app.ui.adapters;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.models.Article;

public class NearbyArticleAdapter extends RecyclerView.Adapter<NearbyArticleViewHolder> {
    private static final String TAG = "NearbyArticleAdapter";
    private final Context mContext;
    private String mArticleType;
    private List<Article> mArticleList = new ArrayList<>();
    private List<Article> mFilteredArticleList = new ArrayList<>();
    private List<Article> mPreSearchFilteredArticleList = new ArrayList<>();

    public NearbyArticleAdapter(final Context context) {
        mContext = context;
    }

    @Override
    public int getItemViewType(int position) {
        Article article = mFilteredArticleList.get(position);
        if (article.getType() == null || article.getType().equals("article")) {
            return 0;
        } else {
            return 1;
        }
    }

    @NonNull
    @Override
    public NearbyArticleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        mArticleType = viewType == 0 ? "article" : "post";
        LayoutInflater inflater = LayoutInflater.from(mContext);
        view = inflater.inflate(R.layout.item_article_list, parent, false);
        return new NearbyArticleViewHolder(mContext, view, mArticleType);
    }

    @Override
    public void onBindViewHolder(@NonNull NearbyArticleViewHolder holder, int position) {
        Article article = mFilteredArticleList.get(position);
        holder.bind(article);
    }

    @Override
    public int getItemCount() {
        return mFilteredArticleList.size();
    }

    public void setList(List<Article> newList, List<String> themeList) {
        mArticleList = new ArrayList<>(newList);
        filterByThemes(themeList);
        notifyDataSetChanged();
    }

    public void setList(List<Article> newList) {
        mArticleList = new ArrayList<>(newList);
        mFilteredArticleList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    public List<Article> getList() {
        return mFilteredArticleList;
    }

    public Article getLastItem() {
        return mFilteredArticleList.get(mFilteredArticleList.size()-1);
    }

    public void clear() {
        mFilteredArticleList.clear();
    }

    public void filterByThemes(List<String> themeFilterList) {
        mFilteredArticleList.clear();
        if (themeFilterList.size() == 0) {
            mFilteredArticleList.addAll(mArticleList);
            notifyDataSetChanged();
            return;
        }
        for (Article article: mArticleList) {
            for (String theme: themeFilterList) {
                if (article.getMainTheme().equals(theme)) {
                    mFilteredArticleList.add(article);
                }
            }
        }
        notifyDataSetChanged();
    }
}
