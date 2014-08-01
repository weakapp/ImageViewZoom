package it.sephiroth.android.library.imagezoom;

import it.sephiroth.android.library.imagezoom.easing.Cubic;
import it.sephiroth.android.library.imagezoom.easing.Easing;
import it.sephiroth.android.library.imagezoom.graphics.FastBitmapDrawable;
import it.sephiroth.android.library.imagezoom.utils.IDisposable;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * Base View to manage image zoom/scrool/pinch operations
 * 
 * @author alessandro
 * 
 */
public class ImageViewTouchBase extends ImageView implements IDisposable {

	private static enum IMAGE_ZOOM_TYPE {
		ZOOM_TYPE_FIT_TO_SCRREN_SMALL,
		ZOOM_TYPE_FIT_TO_SCRREN_BIG, 
		ZOOM_TYPE_FIT_TO_WIDTH, 
		ZOOM_TYPE_FIT_TO_HEIGHT, 
		ZOOM_TYPE_FILL_TO_SCRREN
	};

	public interface OnBitmapChangedListener {

		void onBitmapChanged(Drawable drawable);
	};

	protected static final float MIN_ZOOM = 0.9f;
	protected Easing mEasing = new Cubic();
	protected Matrix mSuppMatrix = new Matrix();
	
	protected Matrix[] mBaseMatrixX = {new Matrix(), new Matrix()};
	protected Drawable[] mDrawables = {null, null};
	protected int mCurrentUseDrawable = 0;
	
	protected Handler mHandler = new Handler();
	protected Runnable mOnLayoutRunnable = null;
	protected float mMaxZoom;
	protected float mMinZoom = 0.1f;
	protected final Matrix mDisplayMatrix = new Matrix();
	protected final float[] mMatrixValues = new float[9];
	protected int mThisWidth = -1, mThisHeight = -1;

    protected float mDefaultMaxZoom = -1f;
    final protected float MAX_ZOOM = 3.0f;
	final protected int DEFAULT_ANIMATION_DURATION = 200;

	protected IMAGE_ZOOM_TYPE mImageZoomType = IMAGE_ZOOM_TYPE.ZOOM_TYPE_FIT_TO_SCRREN_SMALL;

	protected RectF mBitmapRect = new RectF();
	protected RectF mCenterRect = new RectF();
	protected RectF mScrollRect = new RectF();

	private OnBitmapChangedListener mListener;

	public ImageViewTouchBase(Context context) {
		super(context);
		init();
	}

	public ImageViewTouchBase(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	public void setOnBitmapChangedListener(OnBitmapChangedListener listener) {
		mListener = listener;
	}

	protected void init() {
		setScaleType(ImageView.ScaleType.MATRIX);
	}

	public void clear() {
		setImageBitmap(null, true);
	}

    public void setDefaultMaxZoom(float value) {
        mDefaultMaxZoom = value;
        if (mDefaultMaxZoom < 1f) {
            mDefaultMaxZoom = 1f;
        }
    }

    public float getDefaultMaxZoom() {
        return mDefaultMaxZoom;
    }

	public void setFitToScreen(boolean bSmall) {
		if (bSmall) {
			if (mImageZoomType != IMAGE_ZOOM_TYPE.ZOOM_TYPE_FIT_TO_SCRREN_SMALL) {
				mImageZoomType = IMAGE_ZOOM_TYPE.ZOOM_TYPE_FIT_TO_SCRREN_SMALL;
				mSuppMatrix.reset();
				requestLayout();
			}
		} else {
			if (mImageZoomType != IMAGE_ZOOM_TYPE.ZOOM_TYPE_FIT_TO_SCRREN_BIG) {
				mImageZoomType = IMAGE_ZOOM_TYPE.ZOOM_TYPE_FIT_TO_SCRREN_BIG;
				mSuppMatrix.reset();
				requestLayout();
			}
		}
	}

	public void setFitToWidth() {
		if (mImageZoomType != IMAGE_ZOOM_TYPE.ZOOM_TYPE_FIT_TO_WIDTH) {
			mImageZoomType = IMAGE_ZOOM_TYPE.ZOOM_TYPE_FIT_TO_WIDTH;
			mSuppMatrix.reset();
			requestLayout();
		}
	}

	public void setFitToHeight() {
		if (mImageZoomType != IMAGE_ZOOM_TYPE.ZOOM_TYPE_FIT_TO_HEIGHT) {
			mImageZoomType = IMAGE_ZOOM_TYPE.ZOOM_TYPE_FIT_TO_HEIGHT;
			mSuppMatrix.reset();
			requestLayout();
		}
	}

	public void setFillScreen() {
		if (mImageZoomType != IMAGE_ZOOM_TYPE.ZOOM_TYPE_FILL_TO_SCRREN) {
			mImageZoomType = IMAGE_ZOOM_TYPE.ZOOM_TYPE_FILL_TO_SCRREN;
			mSuppMatrix.reset();
			requestLayout();
		}
	}
	
	public boolean goRightImage() {
		if (mDrawables[0] == null) {
			return false;
		}
		if (mCurrentUseDrawable != 0) {
			mCurrentUseDrawable = 0;
			setImageDrawable(mDrawables, true, null, getMaxZoom());	
			requestLayout();
			return true;
		}
		return false;
	}
	
	public boolean goLeftImage() {
		if (mDrawables[1] == null) {
			return false;
		}		
		if (mCurrentUseDrawable != 1) {
			mCurrentUseDrawable = 1;
			setImageDrawable(mDrawables, true, null, getMaxZoom());	
			requestLayout();
			return true;
		}
		return false;
	}

	public void setMinZoom(float value) {
		mMinZoom = value;
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		super.onLayout(changed, left, top, right, bottom);
		mThisWidth = right - left;
		mThisHeight = bottom - top;

		Runnable r = mOnLayoutRunnable;
		if (r != null) {
			mOnLayoutRunnable = null;
			r.run();
		}
		if (getDrawable() != null) {
			
			getProperBaseMatrixInitial(getDrawable(), mBaseMatrixX[mCurrentUseDrawable]);
			setMinZoom(1.0f);

			setImageMatrix(getImageViewMatrix());
			zoomTo(getScale());
		}
	}

	@Override
	public void setImageBitmap(Bitmap bm) {
		setImageBitmap(bm, true);
	}

	@Override
	public void setImageResource(int resId) {
		setImageDrawable(getContext().getResources().getDrawable(resId));
	}

	/**
	 * Set the new image to display and reset the internal matrix.
	 * 
	 * @param bitmap
	 *            - the {@link Bitmap} to display
	 * @param reset
	 *            - if true the image bounds will be recreated, otherwise the
	 *            old {@link Matrix} is used to display the new bitmap
	 * @see #setImageBitmap(Bitmap)
	 */
	public void setImageBitmap(final Bitmap bitmap, final boolean reset) {
		setImageBitmap(bitmap, reset, null);
	}

	/**
	 * Similar to {@link #setImageBitmap(Bitmap, boolean)} but an optional view
	 * {@link Matrix} can be passed to determine the new bitmap view matrix.<br />
	 * This method is useful if you need to restore a Bitmap with the same
	 * zoom/pan values from a previous state
	 * 
	 * @param bitmap
	 *            - the {@link Bitmap} to display
	 * @param reset
	 * @param matrix
	 *            - the {@link Matrix} to be used to display the new bitmap
	 * 
	 * @see #setImageBitmap(Bitmap, boolean)
	 * @see #setImageBitmap(Bitmap)
	 * @see #getImageViewMatrix()
	 * @see #getDisplayMatrix()
	 */
	public void setImageBitmap(final Bitmap bitmap, final boolean reset,
			Matrix matrix) {
		setImageBitmap(bitmap, reset, matrix, -1);
	}

	/**
	 * 
	 * @param bitmap
	 * @param reset
	 * @param matrix
	 * @param maxZoom
	 *            - maximum zoom allowd during zoom gestures
	 * 
	 * @see #setImageBitmap(Bitmap, boolean, Matrix)
	 */
	public void setImageBitmap(final Bitmap bitmap, final boolean reset,
			Matrix matrix, float maxZoom) {

		mCurrentUseDrawable = 0;
		if (bitmap != null) {
			mDrawables[0] = new FastBitmapDrawable(bitmap);
			setImageDrawable(mDrawables, reset, matrix, maxZoom);
		} else {
			setImageDrawable(null, reset, matrix, maxZoom);
		}
	}

    public void setImageBitmap2(final Bitmap leftBitmap, boolean reset) {
        setImageBitmap2(null, leftBitmap, reset, 1);
    }

	public void setImageBitmap2(final Bitmap rightBitmap, final Bitmap leftBitmap, int useBitmap) {
        setImageBitmap2(rightBitmap, leftBitmap, true, useBitmap);
	}

    public void setImageBitmap2(final Bitmap rightBitmap, final Bitmap leftBitmap, boolean reset, int useBitmap) {
        int i = 0;
        for (i = 0;i < mDrawables.length; i++) {
            mDrawables[i] = null;
            mBaseMatrixX[i].reset();
        }

        if (rightBitmap != null) {
            mDrawables[0] = new FastBitmapDrawable(rightBitmap);
        }

        if (leftBitmap != null) {
            mDrawables[1] = new FastBitmapDrawable(leftBitmap);
        }

        mCurrentUseDrawable = useBitmap;

        setImageDrawable(mDrawables, reset, null, -1f);
        forceLayout();
    }

	@Override
	public void setImageDrawable(Drawable drawable) {
		mCurrentUseDrawable = 0;
		mDrawables[0] = drawable;
		setImageDrawable(mDrawables, true, null, -1);
	}

	public void setImageDrawable(final Drawable[] drawables, final boolean reset,
			final Matrix initial_matrix, final float maxZoom) {

		final int viewWidth = getWidth();

		if (viewWidth <= 0) {
			mOnLayoutRunnable = new Runnable() {

				@Override
				public void run() {
					setImageDrawable(drawables, reset, initial_matrix, maxZoom);
				}
			};
			return;
		}

		_setImageDrawable(drawables, reset, initial_matrix, maxZoom);
	}

	protected void _setImageDrawable(final Drawable[] drawable,
			final boolean reset, final Matrix initial_matrix,
			final float maxZoom) {

		if (drawable != null) {
			int i = 0;
			for (i = 0; i < mBaseMatrixX.length; i++) {
				if (drawable[i] != null) {
					getProperBaseMatrixInitial(drawable[i], mBaseMatrixX[i]);
				}
			}

			super.setImageDrawable(drawable[mCurrentUseDrawable]);

		} else {
			int i = 0;
			for (i = 0; i < mBaseMatrixX.length; i++) {
				mBaseMatrixX[i].reset();
			}
			super.setImageDrawable(null);
		}

		if (reset) {
			mSuppMatrix.reset();
		}

		if (initial_matrix != null) {
			mSuppMatrix = new Matrix(initial_matrix);
		}

		setImageMatrix(getImageViewMatrix());

		if (maxZoom < 1)
			mMaxZoom = maxZoom();
		else
			mMaxZoom = maxZoom;

		if (drawable == null) {
			onBitmapChanged(null);
		} else {
			onBitmapChanged(drawable[mCurrentUseDrawable]);
		}
	}	
	

	protected void onBitmapChanged(final Drawable bitmap) {
		if (mListener != null) {
			mListener.onBitmapChanged(bitmap);
		}
	}

	/**
	 * compute the max allowed zoom fator
	 * 
	 * @return
	 */
	protected float maxZoom() {
		final Drawable drawable = getDrawable();

		if (drawable == null) {
			return 1F;
		}

        float max;
        float fw = (float) drawable.getIntrinsicWidth() / (float) mThisWidth;
        float fh = (float) drawable.getIntrinsicHeight() / (float) mThisHeight;
        //float max = Math.max( fw, fh ) * 4;
        max = (1.0f / Math.min(fw, fh)) * 4;

        if (max > MAX_ZOOM) {
            max = MAX_ZOOM;
        }

		return max;
	}

	protected float minZoom() {
		return 1F;
	}

	public float getMaxZoom() {
		if (mMaxZoom < 1) {
			mMaxZoom = maxZoom();
		}
		return mMaxZoom;
	}

	public float getMinZoom() {
		if (mMinZoom < 0) {
			mMinZoom = minZoom();
		}
		//return mMinZoom;
		return 0.1f;
	}

	public Matrix getImageViewMatrix() {
		mDisplayMatrix.set(mBaseMatrixX[mCurrentUseDrawable]);
		mDisplayMatrix.postConcat(mSuppMatrix);
		return mDisplayMatrix;
	}

	/**
	 * Returns the current image display matrix. This matrix can be used in the
	 * next call to the {@link #setImageBitmap(Bitmap, boolean, Matrix)} to
	 * restore the same view state of the previous {@link Bitmap}
	 * 
	 * @return
	 */
	public Matrix getDisplayMatrix() {
		return new Matrix(mSuppMatrix);
	}

	/**
	 * Setup the base matrix so that the image is centered and scaled properly.
	 * 
	 * @param bitmap
	 * @param matrix
	 */

	protected void getProperBaseMatrixInitial(Drawable drawable, Matrix matrix) {
		float viewWidth = getWidth();
		float viewHeight = getHeight();
		float w = drawable.getIntrinsicWidth();
		float h = drawable.getIntrinsicHeight();
		float tw = 0.0f;
		float th = 0.0f;

		matrix.reset();

		float widthScale = Math.min(viewWidth / w, mMaxZoom);
		float heightScale = Math.min(viewHeight / h, mMaxZoom);
		float scale = 0.0f;

		switch (mImageZoomType) {
		case ZOOM_TYPE_FIT_TO_SCRREN_SMALL:
			scale = Math.min(widthScale, heightScale);
			widthScale = scale;
			heightScale = scale;
			break;

		case ZOOM_TYPE_FIT_TO_SCRREN_BIG:
			/** Landscape **/
			if (viewWidth > viewHeight) {
				scale = widthScale;
				th = h;
			} else {
				scale = heightScale;
				tw = -w;
			}
			widthScale = scale;
			heightScale = scale;
			
			break;

		case ZOOM_TYPE_FIT_TO_WIDTH:
			heightScale = widthScale;
			th = h;
			break;
		case ZOOM_TYPE_FIT_TO_HEIGHT:
			widthScale = heightScale;
			tw = -w;
			break;

		default:
			break;
		}

		matrix.postScale(widthScale, heightScale);

		matrix.postTranslate(tw, th);
	
	}

	/**
	 * Setup the base matrix so that the image is centered and scaled properly.
	 * 
	 * @param bitmap
	 * @param matrix
	 */
	protected void getProperBaseMatrix(Drawable drawable, Matrix matrix) {
		float viewWidth = getWidth();
		float viewHeight = getHeight();
		float w = drawable.getIntrinsicWidth();
		float h = drawable.getIntrinsicHeight();
		matrix.reset();

		if (w > viewWidth || h > viewHeight) {
			float widthScale = Math.min(viewWidth / w, MAX_ZOOM);
			float heightScale = Math.min(viewHeight / h, MAX_ZOOM);
			float scale = Math.min(widthScale, heightScale);
			matrix.postScale(scale, scale);
			float tw = (viewWidth - w * scale) / MAX_ZOOM;
			float th = (viewHeight - h * scale) / MAX_ZOOM;
			matrix.postTranslate(tw, th);
		} else {
			float tw = (viewWidth - w) / MAX_ZOOM;
			float th = (viewHeight - h) / MAX_ZOOM;
			matrix.postTranslate(tw, th);
		}
	}

	protected float getValue(Matrix matrix, int whichValue) {
		matrix.getValues(mMatrixValues);
		return mMatrixValues[whichValue];
	}

	public RectF getBitmapRect() {
		return getBitmapRect(mSuppMatrix);
	}

	protected RectF getBitmapRect(Matrix supportMatrix) {
		final Drawable drawable = getDrawable();

		if (drawable == null)
			return null;
		Matrix m = getImageViewMatrix();
		mBitmapRect.set(0, 0, drawable.getIntrinsicWidth(),
				drawable.getIntrinsicHeight());
		m.mapRect(mBitmapRect);
		return mBitmapRect;
	}

	protected float getScale(Matrix matrix) {
		return getValue(matrix, Matrix.MSCALE_X);
	}

	public float getRotation() {
		return 0;
	}

	public float getScale() {
		return getScale(mSuppMatrix);
	}
	
	public float getRealScale() {
		return getScale(mBaseMatrixX[mCurrentUseDrawable]);
	}

	protected void center(boolean horizontal, boolean vertical) {
		final Drawable drawable = getDrawable();

		if (drawable == null)
			return;
		RectF rect = getCenter(mSuppMatrix, horizontal, vertical);

		if (rect.left != 0 || rect.top != 0) {
			postTranslate(rect.left, rect.top);
		}
	}

	protected RectF getCenter(Matrix supportMatrix, boolean horizontal,
			boolean vertical) {
		final Drawable drawable = getDrawable();

		if (drawable == null)
			return new RectF(0, 0, 0, 0);

		mCenterRect.set(0, 0, 0, 0);
		RectF rect = getBitmapRect(supportMatrix);
		float height = rect.height();
		float width = rect.width();
		float deltaX = 0, deltaY = 0;

		if (vertical) {
			int viewHeight = getHeight();
			if (height < viewHeight) {
				deltaY = (viewHeight - height) / 2 - rect.top;
			} else if (rect.top > 0) {
				deltaY = -rect.top;
			} else if (rect.bottom < viewHeight) {
				deltaY = getHeight() - rect.bottom;
			}
		}
		if (horizontal) {
			int viewWidth = getWidth();
			if (width < viewWidth) {
				deltaX = (viewWidth - width) / 2 - rect.left;
			} else if (rect.left > 0) {
				deltaX = -rect.left;
			} else if (rect.right < viewWidth) {
				deltaX = viewWidth - rect.right;
			}
		}
		mCenterRect.set(deltaX, deltaY, 0, 0);
		return mCenterRect;
	}

	protected void postTranslate(float deltaX, float deltaY) {
		mSuppMatrix.postTranslate(deltaX, deltaY);
		setImageMatrix(getImageViewMatrix());
	}

	protected void postScale(float scale, float centerX, float centerY) {
		mSuppMatrix.postScale(scale, scale, centerX, centerY);
		setImageMatrix(getImageViewMatrix());
	}

	protected void zoomTo(float scale) {
		float cx = getWidth() / 2F;
		float cy = getHeight() / 2F;
		zoomTo(scale, cx, cy);
	}

	public void zoomTo(float scale, float durationMs) {
		float cx = getWidth() / 2F;
		float cy = getHeight() / 2F;
		zoomTo(scale, cx, cy, durationMs);
	}

	public void centerTo(float centerX, float centerY) {
		Drawable d = getDrawable();
		if (d == null) {
			return;
		}

		float screenMidX = getWidth() / 2;
		float screenMidY = getHeight() / 2;

		float dx = centerX - screenMidX;
		float dy = centerY - screenMidY;

		scrollBy(-dx, -dy, 500);
	}

	public void centerToImage(float centerX, float centerY) {
		Drawable d = getDrawable();
		float xp = 0;
		float yp = 0;

		if (d == null) {
			return; // nothing to do
		}
		// get the bitmap rect, which represents the rectangle of the view at
		// the specified scale
		// NOTE: this currently has no relation to the actual bitmap...we will
		// provide that relation in this method
		RectF rect = getBitmapRect();
		float screenMidX = getWidth() / 2;
		float screenMidY = getHeight() / 2;
		// determine the centerX and centerY of the image on screen (these
		// coordinates are scaled image coordinates)
		float viewCenterX = -(rect.left - screenMidX);
		float viewCenterY = -(rect.top - screenMidY);

		if (centerX >= 0) {
			// NOTE: postTranslate expects - numbers to pull the image right and
			// +
			// to pull it left

			xp = viewCenterX - (centerX / d.getIntrinsicWidth())
					* (rect.right - rect.left);
		}

		if (centerY >= 0) {
			yp = viewCenterY - (centerY / d.getIntrinsicHeight())
					* (rect.bottom - rect.top);
		}

		scrollBy(xp, yp, 500);
	}

	protected void zoomTo(float scale, float centerX, float centerY) {
		if (scale > mMaxZoom)
			scale = mMaxZoom;
		float oldScale = getScale();
		float deltaScale = scale / oldScale;

		postScale(deltaScale, centerX, centerY);
		onZoom(getScale());
		center(true, true);
	}

	protected void onZoom(float scale) {
	}

	protected void onZoomAnimationCompleted(float scale) {
	}

	public void scrollBy(float x, float y) {
		panBy(x, y);
	}

	protected void panBy(double dx, double dy) {
		RectF rect = getBitmapRect();
		mScrollRect.set((float) dx, (float) dy, 0, 0);
		updateRect(rect, mScrollRect);
		postTranslate(mScrollRect.left, mScrollRect.top);
		center(true, true);
	}

	protected void updateRect(RectF bitmapRect, RectF scrollRect) {
		if (bitmapRect == null) {
			return;
		}
		float width = getWidth();
		float height = getHeight();

		if (bitmapRect.top >= 0 && bitmapRect.bottom <= height)
			scrollRect.top = 0;
		if (bitmapRect.left >= 0 && bitmapRect.right <= width)
			scrollRect.left = 0;
		if (bitmapRect.top + scrollRect.top >= 0 && bitmapRect.bottom > height)
			scrollRect.top = (int) (0 - bitmapRect.top);
		if (bitmapRect.bottom + scrollRect.top <= (height - 0)
				&& bitmapRect.top < 0)
			scrollRect.top = (int) ((height - 0) - bitmapRect.bottom);
		if (bitmapRect.left + scrollRect.left >= 0)
			scrollRect.left = (int) (0 - bitmapRect.left);
		if (bitmapRect.right + scrollRect.left <= (width - 0))
			scrollRect.left = (int) ((width - 0) - bitmapRect.right);
	}

	protected void scrollBy(float distanceX, float distanceY,
			final double durationMs) {
		final double dx = distanceX;
		final double dy = distanceY;
		final long startTime = System.currentTimeMillis();
		mHandler.post(new Runnable() {

			double old_x = 0;
			double old_y = 0;

			@Override
			public void run() {
				long now = System.currentTimeMillis();
				double currentMs = Math.min(durationMs, now - startTime);
				double x = mEasing.easeOut(currentMs, 0, dx, durationMs);
				double y = mEasing.easeOut(currentMs, 0, dy, durationMs);
				panBy((x - old_x), (y - old_y));
				old_x = x;
				old_y = y;
				if (currentMs < durationMs) {
					mHandler.post(this);
				} else {
					RectF centerRect = getCenter(mSuppMatrix, true, true);
					if (centerRect.left != 0 || centerRect.top != 0)
						scrollBy(centerRect.left, centerRect.top);
				}
			}
		});
	}

	protected void zoomTo(float scale, final float centerX,
			final float centerY, final float durationMs) {

		if (scale > getMaxZoom())
			scale = getMaxZoom();
		
		if (scale < getMinZoom()) {
			scale = getMinZoom();
		}
		
		final long startTime = System.currentTimeMillis();
		final float incrementPerMs = (scale - getScale()) / durationMs;
		final float oldScale = getScale();
		if (durationMs == 0) {
			// special case for instantaneous zoom
			zoomTo(scale, centerX, centerY);
		} else {
			mHandler.post(new Runnable() {

				@Override
				public void run() {
					long now = System.currentTimeMillis();
					float currentMs = Math.min(durationMs, now - startTime);
					float target = oldScale + (incrementPerMs * currentMs);
					zoomTo(target, centerX, centerY);
					if (currentMs < durationMs) {
						mHandler.post(this);
					} else {
						// if ( getScale() < 1f ) {}
						//onZoomAnimationCompleted(getScale());
						//center(true, true);
					}
				}
			});
		}
	}

	@Override
	public void dispose() {
		clear();
	}
}
