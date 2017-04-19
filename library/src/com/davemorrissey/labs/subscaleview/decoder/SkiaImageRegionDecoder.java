package com.davemorrissey.labs.subscaleview.decoder;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Pair;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;

import java.io.InputStream;
import java.util.List;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.schedulers.Schedulers;

/**
 * Default implementation of {@link com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder}
 * using Android's {@link android.graphics.BitmapRegionDecoder}, based on the Skia library. This
 * works well in most circumstances and has reasonable performance due to the cached decoder instance,
 * however it has some problems with grayscale, indexed and CMYK images.
 */
public class SkiaImageRegionDecoder implements ImageRegionDecoder {

    private BitmapRegionDecoder decoder;
    private final Object decoderLock = new Object();

    private static final String FILE_PREFIX = "file://";
    private static final String ASSET_PREFIX = FILE_PREFIX + "/android_asset/";
    private static final String RESOURCE_PREFIX = ContentResolver.SCHEME_ANDROID_RESOURCE + "://";

    @Override
    public Observable<Pair<Point, Point>> init(final Context context, final Uri uri) {
        return Observable.create(new ObservableOnSubscribe<Pair<Point, Point>>() {
            @Override
            public void subscribe(final ObservableEmitter<Pair<Point, Point>> observableEmitter) throws Exception {
                String uriString = uri.toString();
                if (uriString.startsWith(RESOURCE_PREFIX)) {
                    Resources res;
                    String packageName = uri.getAuthority();
                    if (context.getPackageName().equals(packageName)) {
                        res = context.getResources();
                    } else {
                        PackageManager pm = context.getPackageManager();
                        res = pm.getResourcesForApplication(packageName);
                    }

                    int id = 0;
                    List<String> segments = uri.getPathSegments();
                    int size = segments.size();
                    if (size == 2 && segments.get(0).equals("drawable")) {
                        String resName = segments.get(1);
                        id = res.getIdentifier(resName, "drawable", packageName);
                    } else if (size == 1 && TextUtils.isDigitsOnly(segments.get(0))) {
                        try {
                            id = Integer.parseInt(segments.get(0));
                        } catch (NumberFormatException ignored) {
                        }
                    }

                    decoder = BitmapRegionDecoder.newInstance(context.getResources().openRawResource(id), false);
                } else if (uriString.startsWith(ASSET_PREFIX)) {
                    String assetName = uriString.substring(ASSET_PREFIX.length());
                    decoder =
                        BitmapRegionDecoder.newInstance(context.getAssets().open(assetName, AssetManager.ACCESS_RANDOM), false);
                } else if (uriString.startsWith(FILE_PREFIX)) {
                    decoder = BitmapRegionDecoder.newInstance(uriString.substring(FILE_PREFIX.length()), false);
                } else {
                    InputStream inputStream = null;
                    try {
                        ContentResolver contentResolver = context.getContentResolver();
                        inputStream = contentResolver.openInputStream(uri);
                        decoder = BitmapRegionDecoder.newInstance(inputStream, false);
                    } finally {
                        if (inputStream != null) {
                            try {
                                inputStream.close();
                            } catch (Exception e) {
                            }
                        }
                    }
                }
                if (!observableEmitter.isDisposed()) {
                    observableEmitter.onNext(Pair.create(new Point(decoder.getWidth(), decoder.getHeight()),
                        SubsamplingScaleImageView.getDefaultTileSize()
                    ));
                    observableEmitter.onComplete();
                }
            }
        }).subscribeOn(Schedulers.single());

    }

    @Override
    public Observable<Bitmap> decodeRegion(final Rect sRect, final int sampleSize) {
        return Observable.create(new ObservableOnSubscribe<Bitmap>() {
            @Override
            public void subscribe(ObservableEmitter<Bitmap> observableEmitter) throws Exception {
                synchronized (decoderLock) {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inSampleSize = sampleSize;
                    options.inPreferredConfig = Config.RGB_565;
                    Bitmap bitmap = decoder.decodeRegion(sRect, options);

                    if (observableEmitter.isDisposed()) return;

                    if (bitmap == null) {
                        observableEmitter.onError(new RuntimeException("Skia image decoder returned null bitmap - image format may not be supported"));
                    } else {
                        observableEmitter.onNext(bitmap);
                        observableEmitter.onComplete();
                    }
                }

            }
        }).subscribeOn(Schedulers.single());
    }

    @Override
    public boolean isReady() {
        return decoder != null && !decoder.isRecycled();
    }

    @Override
    public void recycle() {
        decoder.recycle();
    }
}
