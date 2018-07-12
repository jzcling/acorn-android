package acorn.com.acorn_app.ui.fragments;

import android.app.Dialog;
import android.app.DialogFragment;
import android.os.Bundle;

import acorn.com.acorn_app.ui.viewModels.NotificationViewModel;
import acorn.com.acorn_app.utils.UiUtils;

import static acorn.com.acorn_app.ui.activities.AcornActivity.mNotificationsPref;

public class NotificationsDialogFragment extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog notificationDialog = new UiUtils().configureNotificationDialog(getActivity(),
                mNotificationsPref);
        return notificationDialog;
    }

    @Override
    public void onStop() {
        super.onStop();
        NotificationViewModel.sharedPrefs.postValue(mNotificationsPref);
    }
}
