package acorn.com.acorn_app.data;

import androidx.lifecycle.LiveData;
import android.os.Handler;
import androidx.annotation.NonNull;
import android.util.Log;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import acorn.com.acorn_app.models.Article;

public class ArticleListLiveData extends LiveData<List<Article>> {
    private static final String TAG = "ArticleListLiveData";

    private final DatabaseReference mDatabaseReference = FirebaseDatabase.getInstance().getReference();

    private final Query query;
    public Map<Query, ValueEventListener> savedArticlesQueryList = new HashMap<>();

    // States: 0 = Recent, 1 = Trending, 2 = Saved articles, 3 = Search,
    // -1 = mainTheme, -2 = source
    private int state = 0;
    private int startAt = 0;
    private int limit = 10;

    private final MyChildEventListener childListener = new MyChildEventListener();
    private final MyValueEventListener valueListener = new MyValueEventListener();

    private final List<String> mArticleIds = new ArrayList<>();
    private final List<Article> mArticleList = new ArrayList<>();

    private boolean listenerRemovePending = false;
    private final Handler handler = new Handler();
    private final Runnable removeListener = new Runnable() {
        @Override
        public void run() {
            if (state != 2) {
                query.removeEventListener(childListener);
            } else {
                for (Query query : savedArticlesQueryList.keySet()) {
                    query.removeEventListener(savedArticlesQueryList.get(query));
                }
            }
            listenerRemovePending = false;
        }
    };

    public ArticleListLiveData(Query query) {
        this.query = query;
    }

    public ArticleListLiveData(Query query, int state, int limit, int startAt) {
        this.query = query;
        this.state = state;
        this.limit = limit;
        this.startAt = startAt;
    }

    @Override
    protected void onActive() {
        if (listenerRemovePending) {
            handler.removeCallbacks(removeListener);
        }
        else {
            if (state != 2) {
                query.addChildEventListener(childListener);
            } else {
                query.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        Map<String, Long> savedItems = (Map<String, Long>) dataSnapshot.getValue();
                        if (savedItems == null || savedItems.size() == 0) {
                            return;
                        }

                        List<String> savedIdList = new ArrayList<>(savedItems.keySet());

                        int endIndex = Math.min(startAt + limit, savedIdList.size());
                        for (int i = startAt; i < endIndex; i++) {
                            Query articleQuery = mDatabaseReference.child("article/" + savedIdList.get(i));
                            savedArticlesQueryList.put(articleQuery, valueListener);
                            articleQuery.addValueEventListener(valueListener);
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                    }
                });
            }
        }
        listenerRemovePending = false;
    }

    @Override
    protected void onInactive() {
        handler.postDelayed(removeListener, 500);
        listenerRemovePending = true;
    }

    private class MyChildEventListener implements ChildEventListener {

        @Override
        public void onChildAdded(@NonNull DataSnapshot dataSnapshot, String previousChildKey) {
            Log.d(TAG, "onChildAdded");
            Article article = dataSnapshot.getValue(Article.class);

            if (!mArticleIds.contains(dataSnapshot.getKey())) {
                if (article != null && !article.isReported) {
                    mArticleIds.add(dataSnapshot.getKey());
                    mArticleList.add(article);
                }
            }
            setValue(mArticleList);
        }

        @Override
        public void onChildChanged(@NonNull DataSnapshot dataSnapshot, String previousChildKey) {
            Log.d(TAG, "onChildChanged");
            Article newArticle = dataSnapshot.getValue(Article.class);
            String articleKey = dataSnapshot.getKey();

            int articleIndex = mArticleIds.indexOf(articleKey);
            if (articleIndex > -1) {
                if (newArticle != null) {
                    if (newArticle.isReported) {
                        mArticleIds.remove(articleIndex);
                        mArticleList.remove(articleIndex);
                    } else {
                        // Replace with the new data
                        mArticleList.set(articleIndex, newArticle);
                    }
                }
            }

            setValue(mArticleList);
        }

        @Override
        public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
            Log.d(TAG, "onChildRemoved");
            String articleKey = dataSnapshot.getKey();

            int articleIndex = mArticleIds.indexOf(articleKey);
            if (articleIndex > -1) {
                // Remove data from the list
                mArticleIds.remove(articleIndex);
                mArticleList.remove(articleIndex);
            }

            setValue(mArticleList);
        }

        @Override
        public void onChildMoved(@NonNull DataSnapshot dataSnapshot, String previousChildKey) {
            Log.d(TAG, "onChildMoved");
            Article movedArticle = dataSnapshot.getValue(Article.class);
            String articleKey = dataSnapshot.getKey();

            int oldIndex = mArticleIds.indexOf(articleKey);
            if (oldIndex > -1) {
                // Remove data from old position
                mArticleIds.remove(oldIndex);
                mArticleList.remove(oldIndex);

                // Add data in new position
                int newIndex = previousChildKey == null ? 0 : mArticleIds.indexOf(previousChildKey) + 1;
                mArticleIds.add(articleKey);
                mArticleList.add(newIndex, movedArticle);
            }

            setValue(mArticleList);
        }

        @Override
        public void onCancelled(@NonNull DatabaseError databaseError) {

        }

    }

    private class MyValueEventListener implements ValueEventListener {

        @Override
        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
            Log.d(TAG, "onDataChanged");
            if (dataSnapshot.exists()) {
                Log.d(TAG, "data exists");
                Article article = dataSnapshot.getValue(Article.class);
                String articleId = dataSnapshot.getKey();

                int index = mArticleIds.indexOf(articleId);
                if (index > -1) {
                    mArticleIds.set(index, articleId);
                    mArticleList.set(index, article);
                } else {
                    mArticleIds.add(articleId);
                    mArticleList.add(article);
                }

                setValue(mArticleList);
            }
        }

        @Override
        public void onCancelled(@NonNull DatabaseError databaseError) {

        }
    }
}
