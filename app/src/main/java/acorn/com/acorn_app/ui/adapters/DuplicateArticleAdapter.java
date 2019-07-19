package acorn.com.acorn_app.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import acorn.com.acorn_app.R;
import acorn.com.acorn_app.models.Article;

public class DuplicateArticleAdapter extends RecyclerView.Adapter<DuplicateArticleViewHolder> {
    private static final String TAG = "DuplicateArticleAdapter";
    private final Context mContext;
    private List<Article> mArticleList = new ArrayList<>();

    public DuplicateArticleAdapter(final Context context) {
        mContext = context;
    }

    @NonNull
    @Override
    public DuplicateArticleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        LayoutInflater inflater = LayoutInflater.from(mContext);
        view = inflater.inflate(R.layout.item_duplicate_article_card, parent, false);
        return new DuplicateArticleViewHolder(mContext, view);
    }

    @Override
    public void onBindViewHolder(@NonNull DuplicateArticleViewHolder holder, int position) {
        Article article = mArticleList.get(position);
        holder.bind(article);
    }

    @Override
    public int getItemCount() {
        return mArticleList.size();
    }

    public void setList(List<Article> newList) {
        mArticleList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    public List<Article> getList() {
        return mArticleList;
    }

    public void clear() {
        mArticleList.clear();
    }
}
