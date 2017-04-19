package com.davemorrissey.labs.subscaleview.sample.imagedisplay.decoders;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.util.Pair;

import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import rapid.decoder.BitmapDecoder;

/**
 * A very simple implementation of {@link com.davemorrissey.labs.subscaleview.decoder.ImageRegionDecoder}
 * using the RapidDecoder library (https://github.com/suckgamony/RapidDecoder). For PNGs, this can
 * give more reliable decoding and better performance. For JPGs, it is slower and can run out of
 * memory with large images, but has better support for grayscale and CMYK images.
 *
 * This is an incomplete and untested implementation provided as an example only.
 */
public class RapidImageRegionDecoder implements ImageRegionDecoder {

    private BitmapDecoder decoder;

    @Override
    public Observable<Pair<Point, Point>> init(Context context, Uri uri) {
        decoder = BitmapDecoder.from(context, uri);
        decoder.useBuiltInDecoder(true);
        return Observable.just(
            Pair.create(new Point(decoder.sourceWidth(), decoder.sourceHeight()), SubsamplingScaleImageView.getDefaultTileSize())
        );
    }

    @Override
    public synchronized Observable<Bitmap> decodeRegion(final Rect sRect, final int sampleSize) {
        return Observable.create(new ObservableOnSubscribe<Bitmap>() {
            @Override
            public void subscribe(final ObservableEmitter<Bitmap> observableEmitter) throws Exception {
                try {
                    observableEmitter.onNext(
                        decoder.reset().region(sRect).scale(sRect.width() / sampleSize, sRect.height() / sampleSize).decode());
                } catch (Exception e) {
                    observableEmitter.onError(e);
                }
            }
        });
    }

    @Override
    public boolean isReady() {
        return decoder != null;
    }

    @Override
    public void recycle() {
        BitmapDecoder.destroyMemoryCache();
        BitmapDecoder.destroyDiskCache();
        decoder.reset();
        decoder = null;
    }
}
