package acorn.com.acorn_app.ui.adapters;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.models.Article;

public class SavedArticleAdapter extends RecyclerView.Adapter<SavedArticleViewHolder> {
    private static final String TAG = "SavedArticleAdapter";
    private final Context mContext;
    private String mArticleType;
    private List<Article> mUnfilteredArticleList = new ArrayList<>();
    private List<Article> mFilteredArticleList = new ArrayList<>();
    private List<Article> mPreSearchFilteredArticleList = new ArrayList<>();

    public SavedArticleAdapter(final Context context) {
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
    public SavedArticleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        mArticleType = viewType == 0 ? "article" : "post";
        LayoutInflater inflater = LayoutInflater.from(mContext);
        view = inflater.inflate(R.layout.item_article_list, parent, false);
        return new SavedArticleViewHolder(mContext, view, mArticleType);
    }

    @Override
    public void onBindViewHolder(@NonNull SavedArticleViewHolder holder, int position) {
        Article article = mFilteredArticleList.get(position);
        holder.bind(article);
    }

    @Override
    public int getItemCount() {
        return mFilteredArticleList.size();
    }

    public void setList(List<Article> newList, List<String> themeList, String searchText) {
        mUnfilteredArticleList = new ArrayList<>(newList);
        filterByThemes(themeList);
        filterBySearchText(searchText);
    }

    public List<Article> getList() {
        return mFilteredArticleList;
    }

    public Article getLastItem() {
        return mFilteredArticleList.get(mFilteredArticleList.size()-1);
    }

    public void removeItem(int position) {
        Article article = mFilteredArticleList.get(position);
        mFilteredArticleList.remove(article);
        mPreSearchFilteredArticleList.remove(article);
        mUnfilteredArticleList.remove(article);
        notifyDataSetChanged();
    }

    public void filterByThemes(List<String> themeFilterList) {
        mFilteredArticleList.clear();
        if (themeFilterList.size() == 0) {
            mFilteredArticleList.addAll(mUnfilteredArticleList);
            notifyDataSetChanged();
            return;
        }
        for (Article article: mUnfilteredArticleList) {
            for (String theme: themeFilterList) {
                if (article.getMainTheme().equals(theme)) {
                    mFilteredArticleList.add(article);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void filterBySearchText(String searchText) {
        if (searchText == null || searchText.equals("")) { return; }
        Log.d(TAG, "searchText: " + searchText);
        mPreSearchFilteredArticleList = new ArrayList<>(mFilteredArticleList);
        mFilteredArticleList.clear();
        for (Article article: mPreSearchFilteredArticleList) {
            Log.d(TAG, "searchArticle: " + article.getTitle() + ", " + article.getSource() + ", " + article.getMainTheme());
            if ((article.getTitle() != null && article.getTitle().toLowerCase().contains(searchText)) ||
                    (article.getSource() != null && article.getSource().toLowerCase().contains(searchText)) ||
                    (article.getMainTheme() != null && article.getMainTheme().toLowerCase().contains(searchText)) ||
                    (article.getPostText() != null && article.getPostText().toLowerCase().contains(searchText)) ||
                    (article.getPostAuthor() != null && article.getPostAuthor().toLowerCase().contains(searchText))) {
                mFilteredArticleList.add(article);
            }
        }
        notifyDataSetChanged();
    }

    public void clearSearch() {
        if (mPreSearchFilteredArticleList.size() != 0) {
            mFilteredArticleList = new ArrayList<>(mPreSearchFilteredArticleList);
            mPreSearchFilteredArticleList.clear();
        }
    }
}
