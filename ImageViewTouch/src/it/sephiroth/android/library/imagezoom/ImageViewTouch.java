package it.sephiroth.android.library.imagezoom;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.ScaleGestureDetector.OnScaleGestureListener;
import android.view.ViewConfiguration;

public class ImageViewTouch extends ImageViewTouchBase {

	static final float SCROLL_DELTA_THRESHOLD = 1.0f;
	
	private final static int SCROLL_SINGLE_PAGE_MARGIN = 75;

	protected ScaleGestureDetector mScaleDetector;
	protected GestureDetector mGestureDetector;
	protected int mScaledTouchSlop;
	protected float mCurrentScaleFactor;
	protected float mScaleFactor;
	protected int mDoubleTapDirection;
	protected OnGestureListener mGestureListener;
	protected OnScaleGestureListener mScaleListener;
	protected boolean mDoubleTapEnabled = true;
	protected boolean mScaleEnabled = true;
	protected boolean mOneHandScaleEnabled = true;
	protected boolean mScrollEnabled = true;
	private OnImageViewTouchListener mTapListener = null;

	
	private boolean mEnabledScrollChangeImage = false;
	/***
	 * 
	 * These data are for one hand scale.
	 * 
	 */

	/** Current listener mode */
	private boolean bLastZoomState = false;
	private boolean bZoomState = false;

	/** Y-coordinate of previously handled touch event */
	private float mY;

	/** X-coordinate of latest down event */
	private float mDownX;

	/** Y-coordinate of latest down event */
	private float mDownY;

	/** Duration in ms before a press turns into a long press */
	private int mLongPressTimeout;

	private final Runnable mLongPressRunnable = new Runnable() {
		public void run() {
			bLastZoomState = true;
			bZoomState = true;
			if (mTapListener != null) {
				mTapListener.onImageScaling(true);
			}
		}
	};

	public ImageViewTouch(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	@Override
	protected void init() {
		super.init();
		mScaledTouchSlop = ViewConfiguration.get(getContext())
				.getScaledTouchSlop();
		mGestureListener = getGestureListener();
		mScaleListener = getScaleListener();

		mScaleDetector = new ScaleGestureDetector(getContext(), mScaleListener);
		mGestureDetector = new GestureDetector(getContext(), mGestureListener,
				null, true);

		mCurrentScaleFactor = 1f;
		mDoubleTapDirection = 1;

		/** Used for one hand scale **/
		//mLongPressTimeout = ViewConfiguration.getLongPressTimeout();
		mLongPressTimeout = 300;
		
		mEnabledScrollChangeImage = false;
	}

	public void setTapListener(OnImageViewTouchListener listener) {
		mTapListener = listener;
	}

	public void setDoubleTapEnabled(boolean value) {
		mDoubleTapEnabled = value;
	}

	public void setScaleEnabled(boolean value) {
		mScaleEnabled = value;
	}

	public void setOneHandScaleEnabled(boolean value) {
		mOneHandScaleEnabled = value;
	}

	public void setScrollEnabled(boolean value) {
		mScrollEnabled = value;
	}

	public boolean getDoubleTapEnabled() {
		return mDoubleTapEnabled;
	}

	protected OnGestureListener getGestureListener() {
		return new GestureListener();
	}

	protected OnScaleGestureListener getScaleListener() {
		return new ScaleListener();
	}

	@Override
	protected void onBitmapChanged(Drawable drawable) {
		super.onBitmapChanged(drawable);

		float v[] = new float[9];
		mSuppMatrix.getValues(v);
		mCurrentScaleFactor = v[Matrix.MSCALE_X];
	}

	@Override
	protected void _setImageDrawable(final Drawable[] drawable,
			final boolean reset, final Matrix initial_matrix,
			final float maxZoom) {
		super._setImageDrawable(drawable, reset, initial_matrix, maxZoom);
		mScaleFactor = getMaxZoom() / 3;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!bZoomState) {
			mScaleDetector.onTouchEvent(event);
			if (!mScaleDetector.isInProgress())
				mGestureDetector.onTouchEvent(event);
		}

		int action = event.getAction();

		final float x = event.getX();
		final float y = event.getY();

		switch (action & MotionEvent.ACTION_MASK) {

		case MotionEvent.ACTION_UP:
			if (getScale() < getMinZoom()) {
				zoomTo(getMinZoom(), 50);
			}
			if (mOneHandScaleEnabled) {
				removeCallbacks(mLongPressRunnable);
				bZoomState = false;
			}
			mEnabledScrollChangeImage = false;
			break;

		case MotionEvent.ACTION_DOWN:
			if (mOneHandScaleEnabled) {
				postDelayed(mLongPressRunnable, mLongPressTimeout);
				mDownX = x;
				mDownY = y;
				mY = y;
			}
			break;

		case MotionEvent.ACTION_MOVE: {
			if (mOneHandScaleEnabled) {
				if (mScaleDetector.isInProgress()) {
					removeCallbacks(mLongPressRunnable);

				} else {
					final float dy = (y - mY) / getHeight();

					if (bZoomState) {
						float targetScale = (float) Math.pow(20, -dy)
								* getScale();
						zoomTo(targetScale, mDownX, mDownY);
						mCurrentScaleFactor = Math.min(getMaxZoom(),
								Math.max(targetScale, getMinZoom() - 1.0f));
						invalidate();
					} else {
						final float scrollX = mDownX - x;
						final float scrollY = mDownY - y;

						final float dist = (float) Math.sqrt(scrollX * scrollX
								+ scrollY * scrollY);

						if (dist >= mScaledTouchSlop) {
							removeCallbacks(mLongPressRunnable);
						}
						
						if (mEnabledScrollChangeImage) {
							if (scrollX < (-1*SCROLL_SINGLE_PAGE_MARGIN)) {
								if (mDrawables[1] != null) {
									if (mCurrentUseDrawable != 1) {
										mCurrentUseDrawable = 1;
										super.setImageDrawable(mDrawables, true, null, getMaxZoom());	
										requestLayout();
									}
								}
							} else if (scrollX > SCROLL_SINGLE_PAGE_MARGIN) {
								if (mDrawables[0] != null) {
									if (mCurrentUseDrawable != 0) {
										mCurrentUseDrawable = 0;
										super.setImageDrawable(mDrawables, true, null, getMaxZoom());	
										requestLayout();
									}
								}
							}
						}
					}
					mY = y;
				}
			}
			break;
		}

		default:
			if (mOneHandScaleEnabled) {
				removeCallbacks(mLongPressRunnable);
				bZoomState = false;
			}
			break;
		}
		
		if (!bZoomState) {
			if (bLastZoomState) {
				bLastZoomState = bZoomState;
				if (mTapListener != null) {
					mTapListener.onImageScaling(false);
				}
			}
		}
		
		return true;
	}

	@Override
	protected void onZoom(float scale) {
		super.onZoom(scale);
		if (!mScaleDetector.isInProgress())
			mCurrentScaleFactor = scale;
	}

	@Override
	protected void onZoomAnimationCompleted(float scale) {
		super.onZoomAnimationCompleted(scale);
		if (!mScaleDetector.isInProgress())
			mCurrentScaleFactor = scale;

		if (scale < getMinZoom()) {
			zoomTo(getMinZoom(), 50);
		}
	}

	protected float onDoubleTapPost(float scale, float maxZoom) {
		return 1f;
//		if (mDoubleTapDirection == 1) {
//			mDoubleTapDirection = 0;
//			if ((scale + (mScaleFactor * 2)) <= maxZoom) {
//				return scale + mScaleFactor;
//			} else {
//				mDoubleTapDirection = -1;
//				return maxZoom;
//			}
//		} else if (mDoubleTapDirection == 0) {
//			mDoubleTapDirection = 1;
//			return 1f;
//		} else {
//			mDoubleTapDirection = 1;
//			return 1f;
//		}
	}

	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
			float distanceY) {
		if (!mScrollEnabled)
			return false;

		if (e1 == null || e2 == null)
			return false;
		if (e1.getPointerCount() > 1 || e2.getPointerCount() > 1)
			return false;
		if (mScaleDetector.isInProgress())
			return false;
		
		/** scale = 1.0f not always means we are not zoom it **/
//		if (getScale() == 1f)
//			return false;

		scrollBy(-distanceX, -distanceY);
		invalidate();
		return true;
	}

	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
			float velocityY) {
		if (!mScrollEnabled)
			return false;

		if (e1.getPointerCount() > 1 || e2.getPointerCount() > 1)
			return false;
		if (mScaleDetector.isInProgress())
			return false;

		float diffX = e2.getX() - e1.getX();
		float diffY = e2.getY() - e1.getY();

		if (Math.abs(velocityX) > 800 || Math.abs(velocityY) > 800) {
			scrollBy(diffX / 2, diffY / 2, 300);
			invalidate();
			return true;
		}
		return false;
	}

	/**
	 * Determines whether this ImageViewTouch can be scrolled.
	 * 
	 * @param direction
	 *            - positive direction value means scroll from right to left,
	 *            negative value means scroll from left to right
	 * 
	 * @return true if there is some more place to scroll, false - otherwise.
	 */
	public boolean canScroll(int direction, int x) {
		RectF bitmapRect = getBitmapRect();
		updateRect(bitmapRect, mScrollRect);
		Rect imageViewRect = new Rect();
		
		getGlobalVisibleRect(imageViewRect);

		if (bitmapRect.right >= imageViewRect.right) {
			if (direction < 0) {
				boolean ret = Math.abs(bitmapRect.right - imageViewRect.right) > SCROLL_DELTA_THRESHOLD;
				if (!ret) {
					if (mDrawables[0] != null) {
						if (mCurrentUseDrawable != 0) {
							mEnabledScrollChangeImage = true;
							return true;
						}
					}
				}
				return ret;
			}
		}

		double bitmapScrollRectDelta = Math.abs(bitmapRect.left
				- mScrollRect.left);
		
		boolean ret = bitmapScrollRectDelta > SCROLL_DELTA_THRESHOLD;

		if (!ret) {
			if (mDrawables[1] != null) {
				if (direction > 0) {
					if (mCurrentUseDrawable != 1) {
						mEnabledScrollChangeImage = true;
						return true;
					}
				}
			}
		}
		return ret; 
	}

	public class GestureListener extends
			GestureDetector.SimpleOnGestureListener {

		
		
		@Override
		public boolean onDoubleTap(MotionEvent e) {
			boolean ret = false;

			if (null != mTapListener) {
				ret = mTapListener.onImageDoubleTap(e);
			}

			if (!ret) {
				if (mDoubleTapEnabled) {
					float scale = getScale();
					float targetScale = scale;
					targetScale = onDoubleTapPost(scale, getMaxZoom());
					targetScale = Math.min(getMaxZoom(),
							Math.max(targetScale, getMinZoom()));
					mCurrentScaleFactor = targetScale;
					zoomTo(targetScale, e.getX(), e.getY(),
							DEFAULT_ANIMATION_DURATION);
					invalidate();
				}
			}

			return super.onDoubleTap(e);
		}

		@Override
		public boolean onSingleTapUp(MotionEvent e) {
			boolean ret = false;
			if (mTapListener != null) {
				ret = mTapListener.onImageSingleTapUp(e);
				if (ret) {
					return ret;
				}
			}			
			return super.onSingleTapUp(e);
		}

		@Override
		public boolean onSingleTapConfirmed(MotionEvent e) {
			boolean ret = false;
			if (mTapListener != null) {
				ret = mTapListener.onImageSingleTapUpConfirm(e);
				if (ret) {
					return ret;
				}
			}
			return super.onSingleTapConfirmed(e);
		}

		@Override
		public void onLongPress(MotionEvent e) {
			if (isLongClickable()) {
				if (!mScaleDetector.isInProgress()) {
					setPressed(true);
					performLongClick();
				}
			}
		}

		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2,
				float distanceX, float distanceY) {
			return ImageViewTouch.this.onScroll(e1, e2, distanceX, distanceY);
		}

		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
				float velocityY) {
			return ImageViewTouch.this.onFling(e1, e2, velocityX, velocityY);
		}
	}

	public class ScaleListener extends
			ScaleGestureDetector.SimpleOnScaleGestureListener {

		@Override
		public boolean onScale(ScaleGestureDetector detector) {
			//float span = detector.getCurrentSpan() - detector.getPreviousSpan();
			float targetScale = mCurrentScaleFactor * detector.getScaleFactor();
			if (mScaleEnabled) {
//				targetScale = Math.min(getMaxZoom(),
//						Math.max(targetScale, getMinZoom() - 0.1f));
				zoomTo(targetScale, detector.getFocusX(), detector.getFocusY());
				mCurrentScaleFactor = Math.min(getMaxZoom(),
						Math.max(targetScale, getMinZoom() - 1.0f));
				mDoubleTapDirection = 0;
				invalidate();
				return true;
			}
			return false;
		}

		@Override
		public boolean onScaleBegin(ScaleGestureDetector detector) {
			if (mTapListener != null) {
				mTapListener.onImageScaling(true);
			}
			return super.onScaleBegin(detector);
		}

		@Override
		public void onScaleEnd(ScaleGestureDetector detector) {
			super.onScaleEnd(detector);
			if (getScale() < getMinZoom()) {
				zoomTo(getMinZoom(), 50);
			}			
			if (mTapListener != null) {
				mTapListener.onImageScaling(false);
			}
		}
	}

	public interface OnImageViewTouchListener {
		public boolean onImageDoubleTap(MotionEvent e);

		public boolean onImageSingleTapUpConfirm(MotionEvent e);
		
		public boolean onImageSingleTapUp(MotionEvent e);
		
		public void onImageScaling(boolean bScale);
	}
}
