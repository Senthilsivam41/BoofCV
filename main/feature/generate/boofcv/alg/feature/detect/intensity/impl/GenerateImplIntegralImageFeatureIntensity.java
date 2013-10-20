/*
 * Copyright (c) 2011-2013, Peter Abeles. All Rights Reserved.
 *
 * This file is part of BoofCV (http://boofcv.org).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package boofcv.alg.feature.detect.intensity.impl;

import boofcv.misc.AutoTypeImage;
import boofcv.misc.CodeGeneratorBase;
import boofcv.misc.CodeGeneratorUtil;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;


/**
 * @author Peter Abeles
 */
public class GenerateImplIntegralImageFeatureIntensity extends CodeGeneratorBase {
	String className = "ImplIntegralImageFeatureIntensity";

	PrintStream out;

	public GenerateImplIntegralImageFeatureIntensity() throws FileNotFoundException {
		out = new PrintStream(new FileOutputStream(className + ".java"));
	}

	@Override
	public void generate() throws FileNotFoundException {
		printPreamble();

		printFuncs(AutoTypeImage.F32);
		printFuncs(AutoTypeImage.S32);

		out.print("\n" +
				"}\n");
	}

	private void printPreamble() {
		out.print(CodeGeneratorUtil.copyright);
		out.print("package boofcv.alg.detect.intensity.impl;\n" +
				"\n" +
				"import boofcv.alg.transform.ii.DerivativeIntegralImage;\n" +
				"import boofcv.alg.transform.ii.IntegralImageOps;\n" +
				"import boofcv.alg.transform.ii.IntegralKernel;\n" +
				"import boofcv.struct.image.*;\n" +
				"\n" +
				"\n" +
				"/**\n" +
				" * <p>\n" +
				" * Routines for computing the intensity of the fast hessian features in an image.\n" +
				" * </p>\n" +
				" * \n" +
				" * <p>\n" +
				" * DO NOT MODIFY: Generated by {@link GenerateImplIntegralImageFeatureIntensity}.\n" +
				" * </p>\n" +
				" * \n" +
				" * @author Peter Abeles\n" +
				" */\n" +
				"public class "+className+" {\n\n");
	}

	private void printFuncs(AutoTypeImage input ) {
		printNaive(input);
		border(input);
		computeIntensity(input);
		inner(input);
	}

	private void printNaive( AutoTypeImage input ) {
		out.print("\t/**\n" +
				"\t * Brute force approach which is easy to validate through visual inspection.\n" +
				"\t */\n" +
				"\tpublic static void hessianNaive( "+input.getSingleBandName()+" integral, int skip , int size ,\n" +
				"\t\t\t\t\t\t\t\t\t ImageFloat32 intensity)\n" +
				"\t{\n" +
				"\t\tfinal int w = intensity.width;\n" +
				"\t\tfinal int h = intensity.height;\n" +
				"\n" +
				"\t\t// get convolution kernels for the second order derivatives\n" +
				"\t\tIntegralKernel kerXX = DerivativeIntegralImage.kernelDerivXX(size);\n" +
				"\t\tIntegralKernel kerYY = DerivativeIntegralImage.kernelDerivYY(size);\n" +
				"\t\tIntegralKernel kerXY = DerivativeIntegralImage.kernelDerivXY(size);\n" +
				"\n" +
				"\t\tfloat norm = 1.0f/(size*size);\n" +
				"\n" +
				"\t\tfor( int y = 0; y < h; y++ ) {\n" +
				"\t\t\tfor( int x = 0; x < w; x++ ) {\n" +
				"\n" +
				"\t\t\t\tint xx = x*skip;\n" +
				"\t\t\t\tint yy = y*skip;\n" +
				"\n" +
				"\t\t\t\tcomputeHessian(integral, intensity, kerXX, kerYY, kerXY, norm, y, yy, x, xx);\n" +
				"\t\t\t}\n" +
				"\t\t}\n" +
				"\t}\n\n");
	}

	private void border( AutoTypeImage input ) {
		out.print("\t/**\n" +
				"\t * Only computes the fast hessian along the border using a brute force approach\n" +
				"\t */\n" +
				"\tpublic static void hessianBorder( "+input.getSingleBandName()+" integral, int skip , int size ,\n" +
				"\t\t\t\t\t\t\t\t\t  ImageFloat32 intensity)\n" +
				"\t{\n" +
				"\t\tfinal int w = intensity.width;\n" +
				"\t\tfinal int h = intensity.height;\n" +
				"\n" +
				"\t\t// get convolution kernels for the second order derivatives\n" +
				"\t\tIntegralKernel kerXX = DerivativeIntegralImage.kernelDerivXX(size);\n" +
				"\t\tIntegralKernel kerYY = DerivativeIntegralImage.kernelDerivYY(size);\n" +
				"\t\tIntegralKernel kerXY = DerivativeIntegralImage.kernelDerivXY(size);\n" +
				"\n" +
				"\t\tint radiusFeature = size/2;\n" +
				"\t\tfinal int borderOrig = radiusFeature+ 1 + (skip-(radiusFeature+1)%skip);\n" +
				"\t\tfinal int border = borderOrig/skip;\n" +
				"\n" +
				"\t\tfloat norm = 1.0f/(size*size);\n" +
				"\n" +
				"\t\tfor( int y = 0; y < h; y++ ) {\n" +
				"\t\t\tint yy = y*skip;\n" +
				"\t\t\tfor( int x = 0; x < border; x++ ) {\n" +
				"\t\t\t\tint xx = x*skip;\n" +
				"\t\t\t\tcomputeHessian(integral, intensity, kerXX, kerYY, kerXY, norm, y, yy, x, xx);\n" +
				"\t\t\t}\n" +
				"\t\t\tfor( int x = w-border; x < w; x++ ) {\n" +
				"\t\t\t\tint xx = x*skip;\n" +
				"\t\t\t\tcomputeHessian(integral, intensity, kerXX, kerYY, kerXY, norm, y, yy, x, xx);\n" +
				"\t\t\t}\n" +
				"\t\t}\n" +
				"\n" +
				"\t\tfor( int x = border; x < w-border; x++ ) {\n" +
				"\t\t\tint xx = x*skip;\n" +
				"\n" +
				"\t\t\tfor( int y = 0; y < border; y++ ) {\n" +
				"\t\t\t\tint yy = y*skip;\n" +
				"\t\t\t\tcomputeHessian(integral, intensity, kerXX, kerYY, kerXY, norm, y, yy, x, xx);\n" +
				"\t\t\t}\n" +
				"\t\t\tfor( int y = h-border; y < h; y++ ) {\n" +
				"\t\t\t\tint yy = y*skip;\n" +
				"\t\t\t\tcomputeHessian(integral, intensity, kerXX, kerYY, kerXY, norm, y, yy, x, xx);\n" +
				"\t\t\t}\n" +
				"\t\t}\n" +
				"\t}\n\n");
	}

	private void computeIntensity( AutoTypeImage input ) {
		out.print("\tprivate static void computeHessian("+input.getSingleBandName()+" integral, ImageFloat32 intensity, IntegralKernel kerXX, IntegralKernel kerYY, IntegralKernel kerXY, float norm, int y, int yy, int x, int xx) {\n" +
				"\t\tfloat Dxx = IntegralImageOps.convolveSparse(integral,kerXX,xx,yy);\n" +
				"\t\tfloat Dyy = IntegralImageOps.convolveSparse(integral,kerYY,xx,yy);\n" +
				"\t\tfloat Dxy = IntegralImageOps.convolveSparse(integral,kerXY,xx,yy);\n" +
				"\n" +
				"\t\tDxx *= norm;\n" +
				"\t\tDxy *= norm;\n" +
				"\t\tDyy *= norm;\n" +
				"\n" +
				"\t\tfloat det = Dxx*Dyy-0.81f*Dxy*Dxy;\n" +
				"\n" +
				"\t\tintensity.set(x,y,det);\n" +
				"\t}\n\n");
	}

	private void inner( AutoTypeImage input ) {

		out.print("\t/**\n" +
				"\t * Optimizes intensity for the inner image.  \n" +
				"\t */\n" +
				"\tpublic static void hessianInner( "+input.getSingleBandName()+" integral, int skip , int size ,\n" +
				"\t\t\t\t\t\t\t\t\t ImageFloat32 intensity)\n" +
				"\t{\n" +
				"\t\tfinal int w = intensity.width;\n" +
				"\t\tfinal int h = intensity.height;\n" +
				"\n" +
				"\t\tfloat norm = 1.0f/(size*size);\n" +
				"\n" +
				"\t\tint blockSmall = size/3;\n" +
				"\t\tint blockLarge = size-blockSmall-1;\n" +
				"\t\tint radiusFeature = size/2;\n" +
				"\t\tint radiusSkinny = blockLarge/2;\n" +
				"\n" +
				"\t\tint blockW2 = 2*blockSmall;\n" +
				"\t\tint blockW3 = 3*blockSmall;\n" +
				"\n" +
				"\n" +
				"\t\tint rowOff1 = blockSmall*integral.stride;\n" +
				"\t\tint rowOff2 = 2*rowOff1;\n" +
				"\t\tint rowOff3 = 3*rowOff1;\n" +
				"\n" +
				"\t\t// make sure it starts on the correct pixel\n" +
				"\t\tfinal int borderOrig = radiusFeature+ 1 + (skip-(radiusFeature+1)%skip);\n" +
				"\t\tfinal int border = borderOrig/skip;\n" +
				"\t\tfinal int lostPixel = borderOrig - radiusFeature-1;\n" +
				"\t\tfinal int endY = h - border;\n" +
				"\t\tfinal int endX = w - border;\n" +
				"\n" +
				"\t\tfor( int y = border; y < endY; y++ ) {\n" +
				"\n" +
				"\t\t\t// pixel location in original input image\n" +
				"\t\t\tint yy = y*skip;\n" +
				"\n" +
				"\t\t\t// index for output\n" +
				"\t\t\tint indexDst = intensity.startIndex + y*intensity.stride+border;\n" +
				"\n" +
				"\t\t\t// indexes for Dxx\n" +
				"\t\t\tint indexTop = integral.startIndex + (yy-radiusSkinny-1)*integral.stride+lostPixel;\n" +
				"\t\t\tint indexBottom = indexTop + (blockLarge)*integral.stride;\n" +
				"\n" +
				"\t\t\t// indexes for Dyy\n" +
				"\t\t\tint indexL = integral.startIndex + (yy-radiusFeature-1)*integral.stride + (radiusFeature-radiusSkinny)+lostPixel;\n" +
				"\t\t\tint indexR = indexL + blockLarge;\n" +
				"\n" +
				"\t\t\t// indexes for Dxy\n" +
				"\t\t\tint indexY1 = integral.startIndex + (yy-blockSmall-1)*integral.stride + (radiusFeature-blockSmall)+lostPixel;\n" +
				"\t\t\tint indexY2 = indexY1 + blockSmall*integral.stride;\n" +
				"\t\t\tint indexY3 = indexY2 + integral.stride;\n" +
				"\t\t\tint indexY4 = indexY3 + blockSmall*integral.stride;\n" +
				"\n" +
				"\t\t\tfor( int x = border; x < endX; x++ , indexDst++) {\n" +
				"\t\t\t\tfloat Dxx = integral.data[indexBottom+blockW3] - integral.data[indexTop+blockW3] - integral.data[indexBottom] + integral.data[indexTop];\n" +
				"\t\t\t\tDxx -= 3*(integral.data[indexBottom+blockW2] - integral.data[indexTop+blockW2] - integral.data[indexBottom+blockSmall] + integral.data[indexTop+blockSmall]);\n" +
				"\n" +
				"\t\t\t\tfloat Dyy = integral.data[indexR+rowOff3] - integral.data[indexL+rowOff3] - integral.data[indexR] + integral.data[indexL];\n" +
				"\t\t\t\tDyy -= 3*(integral.data[indexR+rowOff2] - integral.data[indexL+rowOff2] - integral.data[indexR+rowOff1] + integral.data[indexL+rowOff1]);\n" +
				"\n" +
				"\t\t\t\tint x3 = blockSmall+1;\n" +
				"\t\t\t\tint x4 = x3+blockSmall;\n" +
				"\n" +
				"\t\t\t\tfloat Dxy = integral.data[indexY2+blockSmall] - integral.data[indexY1+blockSmall] - integral.data[indexY2] + integral.data[indexY1];\n" +
				"\t\t\t\tDxy -= integral.data[indexY2+x4] - integral.data[indexY1+x4] - integral.data[indexY2+x3] + integral.data[indexY1+x3];\n" +
				"\t\t\t\tDxy += integral.data[indexY4+x4] - integral.data[indexY3+x4] - integral.data[indexY4+x3] + integral.data[indexY3+x3];\n" +
				"\t\t\t\tDxy -= integral.data[indexY4+blockSmall] - integral.data[indexY3+blockSmall] - integral.data[indexY4] + integral.data[indexY3];\n" +
				"\n" +
				"\t\t\t\tDxx *= norm;\n" +
				"\t\t\t\tDxy *= norm;\n" +
				"\t\t\t\tDyy *= norm;\n" +
				"\n" +
				"\t\t\t\tintensity.data[indexDst] = Dxx*Dyy-0.81f*Dxy*Dxy;\n" +
				"\n" +
				"\t\t\t\tindexTop += skip;\n" +
				"\t\t\t\tindexBottom += skip;\n" +
				"\t\t\t\tindexL += skip;\n" +
				"\t\t\t\tindexR += skip;\n" +
				"\t\t\t\tindexY1 += skip;\n" +
				"\t\t\t\tindexY2 += skip;\n" +
				"\t\t\t\tindexY3 += skip;\n" +
				"\t\t\t\tindexY4 += skip;\n" +
				"\t\t\t}\n" +
				"\t\t}\n" +
				"\t}\n\n");
	}

	public static void main( String args[] ) throws FileNotFoundException {
		GenerateImplIntegralImageFeatureIntensity app = new GenerateImplIntegralImageFeatureIntensity();
		app.generate();
	}
}
