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

import com.facebook.ads.Ad;
import com.facebook.ads.AdError;
import com.facebook.ads.NativeAd;
import com.facebook.ads.NativeAdListener;
import com.facebook.ads.NativeAdsManager;
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
import acorn.com.acorn_app.models.Video;
import acorn.com.acorn_app.utils.AppExecutors;
import acorn.com.acorn_app.utils.UiUtils;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mQuery;
import static acorn.com.acorn_app.ui.activities.AcornActivity.mUid;

public class FeedAdapter extends RecyclerView.Adapter<FeedViewHolder> {
    private static final String TAG = "FeedAdapter";
    private final Context mContext;
    private String mItemType;
    private final OnLongClickListener longClickListener;
    public List<Object> mItemList = new ArrayList<>();
    private List<String> mSeenList = new ArrayList<>();

    private NativeAd nativeAd;

    //Data source
    private NetworkDataSource mDataSource;
    private final AppExecutors mExecutors = AppExecutors.getInstance();

    private final Map<DatabaseReference, ValueEventListener> mRefObservedList = new HashMap<>();

    public FeedAdapter(final Context context,
                       @Nullable OnLongClickListener longClickListener) {
        mContext = context;
        this.longClickListener = longClickListener;
        mDataSource = NetworkDataSource.getInstance(mContext, mExecutors);
    }

    @Override
    public int getItemViewType(int position) {

        Object item = mItemList.get(position);
        if (item instanceof Article) {
            if (((Article) item).getType().equals("post")) {
                return 2;
            } else if (((Article) item).duplicates.size() > 0) {
                return 1;
            } else {
                return 0;
            }
        } else if (item instanceof Video) {
            return 3;
        } else if (item instanceof NativeAd) {
            return 4;
        } else {
            return 0;
        }
    }

    @NonNull
    @Override
    public FeedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        LayoutInflater inflater = LayoutInflater.from(mContext);
        if (viewType == 0) {
            mItemType = "article";
            view = inflater.inflate(R.layout.item_article_card, parent, false);
        } else if (viewType == 1) {
            mItemType = "article";
            view = inflater.inflate(R.layout.item_article_card_with_duplicates, parent, false);
        } else if (viewType == 2) {
            mItemType = "post";
            view = inflater.inflate(R.layout.item_post_card, parent, false);
        } else if (viewType == 3) {
            mItemType = "video";
            view = inflater.inflate(R.layout.item_video_card, parent, false);
        } else if (viewType == 4) {
            mItemType = "ad";
            view = inflater.inflate(R.layout.item_ad_card, parent, false);
        } else {
            mItemType = "article";
            view = inflater.inflate(R.layout.item_article_card, parent, false);
        }
        return new FeedViewHolder(mContext, view, mItemType, longClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull FeedViewHolder holder, int position) {
        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(mContext);

        Object item = mItemList.get(position);
        if (item instanceof Video) {
            holder.bindVideo((Video) item);
        } else if (item instanceof Article) {
            holder.bindArticle((Article) item);
        } else if (item instanceof NativeAd) {
            if (((NativeAd) item).isAdInvalidated()) {
                loadNativeAd(() -> holder.bindAd(nativeAd));
            } else {
                holder.bindAd((NativeAd) item);
            }
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
        return mItemList.size();
    }

    public void setList(List<Object> newList, Runnable onComplete) {
        mItemList = new ArrayList<>(newList);
        notifyDataSetChanged();
        onComplete.run();
    }

    public void setList(List<Object> newList) {
        mItemList = new ArrayList<>(newList);
        notifyDataSetChanged();
    }

    public void appendList(List<Object> addList) {
        mItemList.addAll(addList);
        notifyDataSetChanged();
    }

    public List<Object> getList() {
        return mItemList;
    }

    public List<String> getIdList() {
        List<String> idList = new ArrayList<>();
        for (Object item : mItemList) {
            if (item instanceof Article) {
                idList.add(((Article) item).getObjectID());
            } else if (item instanceof Video) {
                idList.add(((Video) item).getObjectID());
//            } else if (item instanceof NativeAd) {
//                idList.add("ad");
            }
        }
        return idList;
    }

    public List<String> getFirstIdList(int count) {
        List<String> idList = new ArrayList<>();
        for (int i = 0; i < count; i ++) {
            if (mItemList.get(i) instanceof Article) {
                idList.add(((Article) mItemList.get(i)).getObjectID());
            } else if (mItemList.get(i) instanceof Video) {
                idList.add(((Video) mItemList.get(i)).getObjectID());
            }
        }
        return idList;
    }

    public Object getLastItem() {
        return mItemList.get(mItemList.size()-1);
    }

    public void clear() {
        mItemList.clear();
        mSeenList.clear();
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

    @Override
    public void onViewAttachedToWindow(@NonNull FeedViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        int position = holder.getAdapterPosition();
        Object item = mItemList.get(position);
        if (item instanceof Article) {
            if (!mSeenList.contains(((Article) item).getObjectID())) {
                mSeenList.add(((Article) item).getObjectID());
            }
        } else if (item instanceof Video) {
            if (!mSeenList.contains(((Video) item).getObjectID())) {
                mSeenList.add(((Video) item).getObjectID());
            }
        }
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull FeedViewHolder holder) {
        super.onViewDetachedFromWindow(holder);
        int position = holder.getAdapterPosition();
        if (position >= 0 && position < mItemList.size()) {
            Object item = mItemList.get(position);
            if (item instanceof Article) {
                if (mSeenList.contains(((Article) item).getObjectID())) {
                    mDataSource.logSeenItemEvent(mUid, ((Article) item).getObjectID(), ((Article) item).getType());
                }
            } else if (item instanceof Video) {
                if (mSeenList.contains(((Video) item).getObjectID())) {
                    mDataSource.logSeenItemEvent(mUid, ((Video) item).getObjectID(), ((Video) item).getType());
                }
            }
        }
    }

    private void loadNativeAd(Runnable onComplete) {
        NativeAd nativeAd = new NativeAd(mContext,
                mContext.getString(R.string.fb_native_ad_placement_id));

        nativeAd.setAdListener(new NativeAdListener() {
            @Override
            public void onMediaDownloaded(Ad ad) {
            }

            @Override
            public void onError(Ad ad, AdError adError) {
                Log.d(TAG, "error loading ad: " + adError.getErrorMessage());
            }

            @Override
            public void onAdLoaded(Ad ad) {
                Log.d(TAG, "ad loaded: " + ad.getPlacementId());
                onComplete.run();
            }

            @Override
            public void onAdClicked(Ad ad) {
            }

            @Override
            public void onLoggingImpression(Ad ad) {
            }
        });

        // Request an ad
        nativeAd.loadAd();
    }
}
