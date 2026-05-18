/*
 * Copyright 2026-present OctoIM contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chat.base.views.blurview.impl;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

public class AndroidXBlurImpl implements BlurImpl {
	private RenderScript mRenderScript;
	private ScriptIntrinsicBlur mBlurScript;
	private Allocation mBlurInput, mBlurOutput;

	@Override
	public boolean prepare(Context context, Bitmap buffer, float radius) {
		if (mRenderScript == null) {
			try {
				mRenderScript = RenderScript.create(context);
				mBlurScript = ScriptIntrinsicBlur.create(mRenderScript, Element.U8_4(mRenderScript));
			} catch (android.renderscript.RSRuntimeException e) {
				if (isDebug(context)) {
					throw e;
				} else {
					// In release mode, just ignore
					release();
					return false;
				}
			}
		}
		mBlurScript.setRadius(radius);

		mBlurInput = Allocation.createFromBitmap(mRenderScript, buffer,
				Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT);
		mBlurOutput = Allocation.createTyped(mRenderScript, mBlurInput.getType());

		return true;
	}

	@Override
	public void release() {
		if (mBlurInput != null) {
			mBlurInput.destroy();
			mBlurInput = null;
		}
		if (mBlurOutput != null) {
			mBlurOutput.destroy();
			mBlurOutput = null;
		}
		if (mBlurScript != null) {
			mBlurScript.destroy();
			mBlurScript = null;
		}
		if (mRenderScript != null) {
			mRenderScript.destroy();
			mRenderScript = null;
		}
	}

	@Override
	public void blur(Bitmap input, Bitmap output) {
		mBlurInput.copyFrom(input);
		mBlurScript.setInput(mBlurInput);
		mBlurScript.forEach(mBlurOutput);
		mBlurOutput.copyTo(output);
	}

	// android:debuggable="true" in AndroidManifest.xml (auto set by build tool)
	static Boolean DEBUG = null;

	static boolean isDebug(Context ctx) {
		if (DEBUG == null && ctx != null) {
			DEBUG = (ctx.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
		}
		return DEBUG.equals(Boolean.TRUE);
	}
}
