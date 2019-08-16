/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package acorn.com.acorn_app.utils;

import android.content.Context;

import acorn.com.acorn_app.data.NetworkDataSource;
import acorn.com.acorn_app.ui.viewModels.FeedViewModelFactory;
import acorn.com.acorn_app.ui.viewModels.VideoViewModelFactory;

public class InjectorUtils {
    public static NetworkDataSource provideNetworkDataSource(Context context) {
        AppExecutors executors = AppExecutors.getInstance();
        return NetworkDataSource.getInstance(context.getApplicationContext(), executors);
    }

    public static FeedViewModelFactory provideArticleViewModelFactory(Context context) {
        NetworkDataSource dataSource = provideNetworkDataSource(context.getApplicationContext());
        return new FeedViewModelFactory(dataSource);
    }

    public static VideoViewModelFactory provideVideoViewModelFactory(Context context) {
        NetworkDataSource dataSource = provideNetworkDataSource(context.getApplicationContext());
        return new VideoViewModelFactory(dataSource);
    }
}