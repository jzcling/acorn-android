package acorn.com.acorn_app.models;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Random;

import static acorn.com.acorn_app.ui.viewModels.ArticleViewModel.QUERY_LIMIT;

public class FbQuery implements Parcelable {
    public final int state;
    public String dbRef = "article";
    public String orderByChild;
    public String orderByKey;
    public String strStartAt = "";
    public Long numStartAt = Long.MAX_VALUE;
    public String strEqualTo = "";
    public Long numEqualTo = Long.MAX_VALUE;
    public int limit = QUERY_LIMIT;

    public FbQuery(Parcel in) {
        state = in.readInt();
        dbRef = in.readString();
        orderByChild = in.readString();
        limit = in.readInt();
        strStartAt = in.readString();
        numStartAt = in.readLong();
        strEqualTo = in.readString();
        numEqualTo = in.readLong();
    }

    // States: 0 = Recent, 1 = Trending, 2 = Saved articles, 3 = Search, 4 = Deals
    // -1 = mainTheme, -2 = source
    // Index Types: 0 = start at index, 1 = equal to index, -1 = start at beginning
    public FbQuery(int state, Object index, int indexType) {
        this.state = state;
        if (state == 0) { // Recent
            orderByChild = "pubDate";
        } else if (state == 1) { // Trending
            orderByChild = "trendingIndex";
        } else if (state == 2) { // Saved articles
            limit = 20;
            orderByChild = "trendingIndex";
        } else if (state == 4) { // Deals
            orderByChild = "trendingIndex";
        }
        if (indexType == 0) {
            strStartAt = index instanceof String ? (String) index : "";
            numStartAt = index instanceof Number ? ((Number) index).longValue() : Long.MAX_VALUE;
        } else if (indexType == 1) {
            strEqualTo = index instanceof String ? (String) index : "";
            numEqualTo = index instanceof Number ? ((Number) index).longValue() : Long.MAX_VALUE;
        }
    }

    public FbQuery(int state, String dbRef, String orderByChild) {
        this.state = state;
        this.dbRef = dbRef;
        this.orderByChild = orderByChild;
    }

    public FbQuery(int state, String dbRef) {
        this.state = state;
        this.dbRef = dbRef;
    }

    public FbQuery(int state, String dbRef, String orderByChild,
                   Object index, int indexType) {
        this.state = state;
        this.dbRef = dbRef;
        this.orderByChild = orderByChild;
        if (indexType == 0) {
            strStartAt = index instanceof String ? (String) index : "";
            numStartAt = index instanceof Number ? ((Number) index).longValue() : Long.MAX_VALUE;

        } else if (indexType == 1) {
            strEqualTo = index instanceof String ? (String) index : "";
            numEqualTo = index instanceof Number ? ((Number) index).longValue() : Long.MAX_VALUE;

        }
    }

    public static final Parcelable.Creator<FbQuery> CREATOR = new Parcelable.Creator<FbQuery>() {
        @Override
        public FbQuery createFromParcel(Parcel source) {
            return new FbQuery(source);
        }

        @Override
        public FbQuery[] newArray(int size) {
            return new FbQuery[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(state);
        dest.writeString(dbRef);
        dest.writeString(orderByChild);
        dest.writeInt(limit);
        dest.writeString(strStartAt);
        dest.writeLong(numStartAt);
        dest.writeString(strEqualTo);
        dest.writeLong(numEqualTo);
    }
}
