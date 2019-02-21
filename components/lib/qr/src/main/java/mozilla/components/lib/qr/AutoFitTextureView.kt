/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mozilla.components.lib.qr

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView
import android.view.View

/**
 * A [TextureView] that can be adjusted to a specified aspect ratio.
 */
class AutoFitTextureView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0) : TextureView(context, attrs, defStyle) {
    private var mRatioWidth = 0
    private var mRatioHeight = 0

    /**
     * Sets the aspect ratio for this view. The size of the view will be measured based on the ratio
     * calculated from the parameters. Note that the actual sizes of parameters don't matter, that
     * is, calling setAspectRatio(2, 3) and setAspectRatio(4, 6) make the same result.
     *
     * @param width  Relative horizontal size
     * @param height Relative vertical size
     */
    fun setAspectRatio(width: Int, height: Int) {
        Log.d(TAG, "setAspectRatio($width, $height)")
        if (width < 0 || height < 0) {
            throw IllegalArgumentException("Size cannot be negative.")
        }
        mRatioWidth = width
        mRatioHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        Log.d(TAG, "onMeasure($widthMeasureSpec, $heightMeasureSpec)")
        val width = View.MeasureSpec.getSize(widthMeasureSpec)
        //int width = MeasureSpec.getSize(1073743264);

        val height = View.MeasureSpec.getSize(heightMeasureSpec)
        Log.d(TAG, "onMeasure2($width, $height)")
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            Log.d(TAG, "onMeasure3($width, $height)")
            setMeasuredDimension(width, height)
        } else {
            Log.d(TAG, "onMeasure4($width, $height)")
            Log.d(TAG, "mRatio($mRatioWidth, $mRatioHeight)")
            if (width < height * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth)
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height)
            }
        }
    }

    companion object {
        private val TAG = "AutoFitTextureView"
    }

}
