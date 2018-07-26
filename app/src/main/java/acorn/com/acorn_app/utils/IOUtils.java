package acorn.com.acorn_app.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.Toast;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;

import static acorn.com.acorn_app.utils.UiUtils.createToast;

public class IOUtils {
    public static String getInputStream(Context context, String urlString) throws IOException {
        URL url = new URL(urlString);
        try {
            InputStream inputStream = new BufferedInputStream(url.openConnection().getInputStream());
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder response = new StringBuilder();

            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    response.append(line).append('\n');
                }
            } finally {
                inputStream.close();
            }
            return response.toString();
        } catch (Exception e) {
            e.printStackTrace();
            createToast(context, "Connection failed. " +
                    "Please check that you are connected to the internet.", Toast.LENGTH_SHORT);
        }
        return null;
    }

    public static Bitmap getBitmapFromUrl(String imageUrl) {
        if (imageUrl == null) {
            return null;
        } else if (imageUrl.startsWith("gs://")) {
            try {
                final Bitmap[] bitmap = new Bitmap[1];
                StorageReference ref = FirebaseStorage.getInstance().getReferenceFromUrl(imageUrl);
                File localFile = File.createTempFile("images", "jpg");
                ref.getFile(localFile).addOnSuccessListener(taskSnapshot -> bitmap[0] = BitmapFactory.decodeFile(localFile.getAbsolutePath()));
                return bitmap[0];
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else {
            try {
                URL url = new URL(imageUrl);
                InputStream input = url.openConnection().getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                return bitmap;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }
    }
}
