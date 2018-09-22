package acorn.com.acorn_app.data;

import android.arch.lifecycle.LiveData;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.util.Log;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import acorn.com.acorn_app.models.Article;

public class ArticleListLiveData extends LiveData<List<Article>> {
    private static final String TAG = "ArticleListLiveData";

    private final DatabaseReference mDatabaseReference = FirebaseDatabase.getInstance().getReference();

    private final Query query;
    private List<Query> savedArticlesQueryList = new ArrayList<>();

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
                Log.d(TAG, "childEventListener removed " + query.toString());
            } else {
                for (Query query : savedArticlesQueryList) {
                    query.removeEventListener(valueListener);
                }
                Log.d(TAG, "valueEventListener removed " + savedArticlesQueryList.toString());
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
            Log.d(TAG, "removeListener callback removed");
        }
        else {
            if (state != 2) {
                Log.d(TAG, "childEventListener added " + query.toString());
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
                            savedArticlesQueryList.add(articleQuery);
                            articleQuery.addValueEventListener(valueListener);
                            Log.d(TAG, "valueEventListener added " + articleQuery.toString());
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {
                        Log.d(TAG, "Failed to get saved articles id list");
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
            Log.d(TAG, "childAdded");
            Article article = dataSnapshot.getValue(Article.class);

            if (!mArticleIds.contains(dataSnapshot.getKey())) {
                mArticleIds.add(dataSnapshot.getKey());
                mArticleList.add(article);
            }
            setValue(mArticleList);
        }

        @Override
        public void onChildChanged(@NonNull DataSnapshot dataSnapshot, String previousChildKey) {
            Log.d(TAG, "childChanged");
            Article newArticle = dataSnapshot.getValue(Article.class);
            String articleKey = dataSnapshot.getKey();

            int articleIndex = mArticleIds.indexOf(articleKey);
            if (articleIndex > -1) {
                // Replace with the new data
                mArticleList.set(articleIndex, newArticle);
            } else {
                Log.w(TAG, "onChildChanged:unknown_child:" + articleKey);
            }

            setValue(mArticleList);
        }

        @Override
        public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {
            Log.d(TAG, "childRemoved");
            String articleKey = dataSnapshot.getKey();

            int articleIndex = mArticleIds.indexOf(articleKey);
            if (articleIndex > -1) {
                // Remove data from the list
                mArticleIds.remove(articleIndex);
                Log.d(TAG, "onChildRemoved: articleIndex: " + articleIndex);
                Log.d(TAG, "onChildRemoved: mArticleListSize: " + mArticleList.size());
                mArticleList.remove(articleIndex);
            } else {
                Log.w(TAG, "onChildRemoved:unknown_child:" + articleKey);
            }

            setValue(mArticleList);
        }

        @Override
        public void onChildMoved(@NonNull DataSnapshot dataSnapshot, String previousChildKey) {
            Log.d(TAG, "childMoved");
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
            Log.w(TAG, "onCancelled", databaseError.toException());
        }

    }

    private class MyValueEventListener implements ValueEventListener {

        @Override
        public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
            Article article = dataSnapshot.getValue(Article.class);
            mArticleList.add(article);
            setValue(mArticleList);
        }

        @Override
        public void onCancelled(@NonNull DatabaseError databaseError) {
            Log.w(TAG, "onCancelled", databaseError.toException());
        }
    }
}
