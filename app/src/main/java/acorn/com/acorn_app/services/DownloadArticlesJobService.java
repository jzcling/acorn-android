package acorn.com.acorn_app.services;

import android.content.SharedPreferences;
import android.util.Log;

import com.firebase.jobdispatcher.JobParameters;
import com.firebase.jobdispatcher.JobService;

import java.util.Date;

import acorn.com.acorn_app.data.ArticleRoomDatabase;
import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.models.Article;
import acorn.com.acorn_app.models.dbArticle;
import acorn.com.acorn_app.utils.AppExecutors;

public class DownloadArticlesJobService extends JobService {
    private static final String TAG = "DownloadArticlesService";
    private AppExecutors mExecutors;
    private NetworkDataSource mDataSource;
    private ArticleRoomDatabase mRoomDb;
            
    @Override
    public boolean onStartJob(JobParameters params) {
        mExecutors = AppExecutors.getInstance();
        mDataSource = NetworkDataSource.getInstance(this, mExecutors);
        mRoomDb = ArticleRoomDatabase.getInstance(this);

        mExecutors.networkIO().execute(
                () -> mDataSource.downloadSubscribedArticles(500, (articleList) -> {
                    Long cutOffDate = (new Date()).getTime() - 2L * 24L* 60L * 60L * 1000L; // more than 2 days ago
                    mExecutors.diskWrite().execute(() -> {
                        mRoomDb.articleDAO().deleteOld(cutOffDate);
                    });
                    jobFinished(params, true);
                }, () -> {}));
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
