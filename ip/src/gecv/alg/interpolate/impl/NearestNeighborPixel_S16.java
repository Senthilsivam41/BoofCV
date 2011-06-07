/*
 * Copyright 2011 Peter Abeles
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package gecv.alg.interpolate.impl;

import gecv.alg.interpolate.InterpolatePixel;
import gecv.struct.image.ImageSInt16;


/**
 * Performs nearest neighbor interpolation to extract values between pixels in an image.
 *
 * @author Peter Abeles
 */
public class NearestNeighborPixel_S16 implements InterpolatePixel<ImageSInt16> {

	private ImageSInt16 orig;

	private short data[];
	private int stride;
	private int width;
	private int height;

	public NearestNeighborPixel_S16() {
	}

	public NearestNeighborPixel_S16(ImageSInt16 orig) {
		setImage(orig);
	}

	@Override
	public void setImage(ImageSInt16 image) {
		this.orig = image;
		this.data = orig.data;
		this.stride = orig.getStride();
		this.width = orig.getWidth();
		this.height = orig.getHeight();
	}

	@Override
	public ImageSInt16 getImage() {
		return orig;
	}

	@Override
	public float get_unsafe(float x, float y) {
		return data[ orig.startIndex + ((int)y)*stride + (int)x];
	}

	@Override
	public float get(float x, float y) {
		int xx = (int)x;
		int yy = (int)y;
		if (xx < 0 || yy < 0 || xx >= width || yy >= height)
			throw new IllegalArgumentException("Point is outside of the image");

		return data[ orig.startIndex + yy*stride + xx];
	}
}
