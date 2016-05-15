/**
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.volley.toolbox;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.ViewGroup.LayoutParams;
import android.widget.ImageView;

import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader.ImageContainer;
import com.android.volley.toolbox.ImageLoader.ImageListener;

/**
 * Handles fetching an image from a URL as well as the life-cycle of the
 * associated request.
 */
public class NetworkImageView extends ImageView {
    /** The URL of the network image to load */
    private String mUrl;

    /**
     * Resource ID of the image to be used as a placeholder until the network image is loaded.
     */
    private int mDefaultImageId;

    /**
     * Resource ID of the image to be used if the network response fails.
     */
    private int mErrorImageId;

    /** Local copy of the ImageLoader. */
    private ImageLoader mImageLoader;

    /** Current ImageContainer. (either in-flight or finished) */
    private ImageContainer mImageContainer;

    public NetworkImageView(Context context) {
        this(context, null);
    }

    public NetworkImageView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NetworkImageView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Sets URL of the image that should be loaded into this view. Note that calling this will
     * immediately either set the cached image (if available) or the default image specified by
     * {@link NetworkImageView#setDefaultImageResId(int)} on the view.
     *
     * NOTE: If applicable, {@link NetworkImageView#setDefaultImageResId(int)} and
     * {@link NetworkImageView#setErrorImageResId(int)} should be called prior to calling
     * this function.
     *
     * @param url The URL that should be loaded into this ImageView.
     * @param imageLoader ImageLoader that will be used to make the request.
     */
    public void setImageUrl(String url, ImageLoader imageLoader) {
        mUrl = url;
        mImageLoader = imageLoader;
        // The URL has potentially changed. See if we need to load it.
        loadImageIfNecessary(false);
    }
    
    /**
     * Gets the URL of the image that should be loaded into this view, or null if no URL has been set.
     * The image may or may not already be downloaded and set into the view.
     * @return the URL of the image to be set into the view, or null.
     */
    public String getImageURL()
    {
    	return mUrl;
    }

    /**
     * Sets the default image resource ID to be used for this view until the attempt to load it
     * completes.
     */
    public void setDefaultImageResId(int defaultImage) {
        mDefaultImageId = defaultImage;
    }

    /**
     * Sets the error image resource ID to be used for this view in the event that the image
     * requested fails to load.
     */
    public void setErrorImageResId(int errorImage) {
        mErrorImageId = errorImage;
    }

    /**
     * Loads the image for the view if it isn't already loaded.
     * @param isInLayoutPass True if this was invoked from a layout pass, false otherwise.
     */
    void loadImageIfNecessary(final boolean isInLayoutPass) {
        int width = getWidth();
        int height = getHeight();
        ScaleType scaleType = getScaleType();

        boolean wrapWidth = false, wrapHeight = false;
        if (getLayoutParams() != null) {
            wrapWidth = getLayoutParams().width == LayoutParams.WRAP_CONTENT;
            wrapHeight = getLayoutParams().height == LayoutParams.WRAP_CONTENT;
        }

        // if the view's bounds aren't known yet, and this is not a wrap-content/wrap-content
        // view, hold off on loading the image.
        boolean isFullyWrapContent = wrapWidth && wrapHeight;
        if (width == 0 && height == 0 && !isFullyWrapContent) {
            return;
        }

        // if the URL to be loaded in this view is empty, cancel any old requests and clear the
        // currently loaded image.
        if (TextUtils.isEmpty(mUrl)) {
            if (mImageContainer != null) {
                mImageContainer.cancelRequest();
                mImageContainer = null;
            }
            setDefaultImageOrNull();
            return;
        }

        // if there was an old request in this view, check if it needs to be canceled.
        if (mImageContainer != null && mImageContainer.getRequestUrl() != null) {
            if (mImageContainer.getRequestUrl().equals(mUrl)) {
                // if the request is from the same URL, return.
                return;
            } else {
                // if there is a pre-existing request, cancel it if it's fetching a different URL.
                mImageContainer.cancelRequest();
                setDefaultImageOrNull();
            }
        }

        // Calculate the max image width / height to use while ignoring WRAP_CONTENT dimens.
        int maxWidth = wrapWidth ? 0 : width;
        int maxHeight = wrapHeight ? 0 : height;

        // The pre-existing content of this view didn't match the current URL. Load the new image
        // from the network.
        ImageContainer newContainer = mImageLoader.get(mUrl,
                new ImageListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        if (mErrorImageId != 0) {
                            setImageResource(mErrorImageId);
                        }
                    }

                    @Override
                    public void onResponse(final ImageContainer response, boolean isImmediate) {
                        // If this was an immediate response that was delivered inside of a layout
                        // pass do not set the image immediately as it will trigger a requestLayout
                        // inside of a layout. Instead, defer setting the image by posting back to
                        // the main thread.
                        if (isImmediate && isInLayoutPass) {
                            post(new Runnable() {
                                @Override
                                public void run() {
                                    onResponse(response, false);
                                }
                            });
                            return;
                        }

                        if (response.getBitmap() != null) {
                            setImageBitmap(response.getBitmap());
                        } else if (mDefaultImageId != 0) {
                            setImageResource(mDefaultImageId);
                        }
                    }
                }, maxWidth, maxHeight, scaleType);

        // update the ImageContainer to be the new bitmap container.
        mImageContainer = newContainer;
    }

    private void setDefaultImageOrNull() {
        if(mDefaultImageId != 0) {
            setImageResource(mDefaultImageId);
        }
        else {
            setImageBitmap(null);
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        loadImageIfNecessary(true);
    }


    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int measureWidth = measureWidth(widthMeasureSpec);
        int measureHeight = measureHeight(heightMeasureSpec);
        setMeasuredDimension(measureWidth, measureHeight);

//        Drawable drawable = getDrawable();
//        if (drawable != null) {
//            int width = MeasureSpec.getSize(widthMeasureSpec);
//            int diw = drawable.getIntrinsicWidth();
//            if (diw > 0) {
//                int height = width * drawable.getIntrinsicHeight() / diw;
//                setMeasuredDimension(width, height);
//            } else
//                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
//        } else
//            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

    }

    private int measureWidth(int pWidthMeasureSpec) {
        int result = 0;
        int widthMode = MeasureSpec.getMode(pWidthMeasureSpec);
        int widthSize = MeasureSpec.getSize(pWidthMeasureSpec);

        switch (widthMode) {
            case MeasureSpec.AT_MOST:
            case MeasureSpec.EXACTLY:
                result = widthSize;
                break;
        }
        return result;
    }

    private int measureHeight(int pHeightMeasureSpec) {
        int result = 0;
        int heightMode = MeasureSpec.getMode(pHeightMeasureSpec);
        int heightSize = MeasureSpec.getSize(pHeightMeasureSpec);
        switch (heightMode) {
            case MeasureSpec.AT_MOST:
            case MeasureSpec.EXACTLY:
                result = heightSize;
                break;
        }
        return result;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mImageContainer != null) {
            // If the view was bound to an image request, cancel it and clear
            // out the image from the view.
            mImageContainer.cancelRequest();
            setImageBitmap(null);
            // also clear out the container so we can reload the image if necessary.
            mImageContainer = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

     @Override
    public void onDraw(Canvas canvas) {
        if (isHexagon) {
            image = drawableToBitmap(getDrawable());
            float scaleSize = 1;
            if (image.getWidth() < getWidth() || image.getHeight() < getHeight()) {
                //六边形getWidth和getHeight大小一样
                image = Bitmap.createScaledBitmap(image, getWidth(), getHeight(), true);
            }
            Bitmap hexagonBitmap = getHexagonRoundedBitmap(image, getWidth());
            canvas.drawBitmap(hexagonBitmap, 0, 0, null);
            if (hexagonBitmap != null || !hexagonBitmap.isRecycled()) {
                hexagonBitmap.recycle();
                hexagonBitmap = null;
            }
        } else super.onDraw(canvas);
//            if (isNeedBlur) {
//                image = drawableToBitmap(getDrawable());
//                float scaleSize = 1;
//                if (image.getWidth() < getWidth() || image.getHeight() < getHeight()) {
//                    scaleSize = Math.max((float) getWidth() / image.getWidth(), (float) getHeight() / image.getHeight());
//                    image = Bitmap.createScaledBitmap(image, (int) (image.getWidth() * scaleSize) + 1, (int) (image.getHeight() * scaleSize) + 1, true);
//                    int dw = image.getWidth() - getWidth();
//                    int dh = image.getHeight() - getHeight();
//                    int offsetX = 0;
//                    int offsetY = 0;
//                    if (dw > dh) {
//                        offsetX = dw / 2;
//                    }
//                    if (image.getHeight() > getHeight())
//                        offsetY = dh / 2;
//                    image = Bitmap.createBitmap(image, offsetX, offsetY, getWidth(), getHeight());
//                }
//                Bitmap finalBitmap = null;
//                finalBitmap = BlurImage.fastblur(getContext(), image, blurSize);
//                canvas.drawBitmap(finalBitmap, 0, 0, null);
//                if (null != finalBitmap && !finalBitmap.isRecycled()) {
//                    finalBitmap.recycle();
//                    finalBitmap = null;
//                }
//                Bitmap temp1 = null, temp2 = null, temp3 = null, temp4 = null, temp5 = null, temp6 = null;
//                if (blurSize > 0) {
//                } else if (blurSize < 0) {
//                    temp1 = BlurImage.fastblur(getContext(), image, blur);
//                    temp2 = BlurImage.fastblur(getContext(), temp1, blur);
//                    temp3 = BlurImage.fastblur(getContext(), temp2, blur);
//                    temp4 = BlurImage.fastblur(getContext(), temp3, blur);
//                    temp5 = BlurImage.fastblur(getContext(), temp4, blur);
//                    temp6 = BlurImage.fastblur(getContext(), temp5, blur);
//                    finalBitmap = BlurImage.fastblur(getContext(), temp6, blur);
//                } else if (blurSize == 0) {
//                    temp1 = BlurImage.fastblur(getContext(), image, blur);
//                    finalBitmap = BlurImage.fastblur(getContext(), temp1, blur);
//                }
//                if (null != temp1 && !temp1.isRecycled()) {
//                    temp1.recycle();
//                    temp1 = null;
//                }
//                if (null != temp2 && !temp2.isRecycled()) {
//                    temp2.recycle();
//                    temp2 = null;
//                }
//                if (null != temp3 && !temp3.isRecycled()) {
//                    temp3.recycle();
//                    temp3 = null;
//                }
//                if (null != temp4 && !temp4.isRecycled()) {
//                    temp4.recycle();
//                    temp4 = null;
//                }
//                if (null != temp5 && !temp5.isRecycled()) {
//                    temp5.recycle();
//                    temp5 = null;
//                }
//                if (null != temp6 && !temp6.isRecycled()) {
//                    temp6.recycle();
//                    temp6 = null;
//                }
//            } else super.onDraw(canvas);

    }

    /**
     * 把Drawable对象转换成Bitmap
     *
     * @param drawable
     * @return
     */
    private Bitmap drawableToBitmap(Drawable drawable) {
        if (drawable == null) {
            return null;
        } else if (drawable instanceof BitmapDrawable) {
            return ((BitmapDrawable) drawable).getBitmap();
        }
        Bitmap bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicWidth());
//        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);

        return bitmap;
    }

    /**
     * 画圆角六边形
     *
     * @param bitmap
     * @param radius
     * @return
     */
    private Bitmap getHexagonRoundedBitmap(Bitmap bitmap, int radius) {
        Bitmap finalBitmap = bitmap;
//        if (bitmap.getWidth() != radius || bitmap.getHeight() != radius)
//            finalBitmap = Bitmap.createScaledBitmap(bitmap, radius, radius,
//                    false);
//        else
//            finalBitmap = bitmap;
        Bitmap output = Bitmap.createBitmap(radius,
                radius, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint();
        paint.setAntiAlias(true);
        final Rect rect = new Rect(0, 0, radius,
                radius);

        //开始画圆角六边形
        //定义6个角的坐标，定义每个角两侧的坐标
        //角的两侧坐标之间画贝塞尔曲线，同一条边的两个点画直线
        Path path = new Path();
        int width = radius;
        int length = width / 2;
        float sinLength = (float) (length * Math.sin(radian60));
        float cosLength = (float) (length * Math.cos(radian60));
        float pX = cosLength / f;
        float pY = sinLength / f;
        float p1X = pX;
        float p1Y = length - pY;
        float p2X = cosLength - pX;
        float p2Y = length - sinLength + pY;
        float p3X = cosLength + pX;
        float p3Y = length - sinLength;
        float p4X = width - cosLength - pX;
        float p4Y = p3Y;
        float p5X = width - cosLength + pX;
        float p5Y = p2Y;
        float p6X = width - pX;
        float p6Y = p1Y;
        float p7X = p6X;
        float p7Y = length + pY;
        float p8X = p5X;
        float p8Y = length + sinLength - pY;
        float p9X = p4X;
        float p9Y = sinLength + length;
        float p10X = p3X;
        float p10Y = p9Y;
        float p11X = p2X;
        float p11Y = p8Y;
        float p12X = p1X;
        float p12Y = p7Y;
        float c1X = 0;
        float c1Y = length;
        float c2X = cosLength;
        float c2Y = length - sinLength;
        float c3X = length + cosLength;
        float c3Y = c2Y;
        float c4X = width;
        float c4Y = length;
        float c5X = c3X;
        float c5Y = length + sinLength;
        float c6X = c2X;
        float c6Y = c5Y;
        path.moveTo(p1X, p1Y);
        path.lineTo(p2X, p2Y);
        path.quadTo(c2X, c2Y, p3X, p3Y);
        path.lineTo(p4X, p4Y);
        path.quadTo(c3X, c3Y, p5X, p5Y);
        path.lineTo(p6X, p6Y);
        path.quadTo(c4X, c4Y, p7X, p7Y);
        path.lineTo(p8X, p8Y);
        path.quadTo(c5X, c5Y, p9X, p9Y);
        path.lineTo(p10X, p10Y);
        path.quadTo(c6X, c6Y, p11X, p11Y);
        path.lineTo(p12X, p12Y);
        path.quadTo(c1X, c1Y, p1X, p1Y);
        path.close();
        canvas.drawPath(path, paint);
        //画原图形，设置重合显示
        canvas.drawARGB(0, 0, 0, 0);
        paint.setColor(Color.parseColor("#BAB399"));
        canvas.drawPath(path, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        canvas.drawBitmap(finalBitmap, rect, rect, paint);

        //画边框
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(w);   // 边框宽度
        paint.setColor(bolderColor);
        canvas.drawPath(path, paint);

//        if (finalBitmap != null || !finalBitmap.isRecycled()) {
//            finalBitmap.recycle();
//            finalBitmap = null;
//        }
        return output;
    }

    /**
     * 另一种六边形路径构建方法
     *
     * @param width
     * @return
     */
    private Path getHexagonPath(int width) {
        Path path = new Path();
        float sinlength = (float) (width * Math.sin(radian60));
        float cX = width / 2;
        float cY = width / 2;
        float p1X = cX - width / 2;
        float p1Y = cY;
        float p2X = cX - cX / 2;
        float p2Y = cY - sinlength;
        float p3X = cX + cX / 2;
        float p3Y = p2Y;
        float p4X = cX + width / 2;
        float p4Y = p1Y;
        float p5X = p3X;
        float p5Y = cY + sinlength;
        float p6X = p2X;
        float p6Y = p5Y;

        float dx = p2X / f;
        float dy = p2Y / f;
        float c1X = p2X - dx;
        float c1Y = p2Y + dy;
        float c2X = p2X + dx;
        float c2Y = p2Y;
        float c3X = p3X - dx;
        float c3Y = p3Y;
        float c4X = p3X + dx;
        float c4Y = c1Y;
        float c5X = p4X - dx;
        float c5Y = p4Y - dy;
        float c6X = c5X;
        float c6Y = p4Y + dy;
        float c7X = c4X;
        float c7Y = p5Y - dy;
        float c8X = p5X - dx;
        float c8Y = p5Y;
        float c9X = c2X;
        float c9Y = p5Y;
        float c10X = c1X;
        float c10Y = c7Y;
        float c11X = p1X + dx;
        float c11Y = c6Y;
        float c12X = c11X;
        float c12Y = c5Y;

        return path;
    }

}
