/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package edu.pdx.imagej.deconv;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

/**
 * Plugin for deconvolving images with an assumed Point Spread Function (PSF)
 * or an experimentally determined PSF. The plugin can use an instant or 
 * iterative method to perform the deconvolution.
 *
 * @author Trevor Schlack
 */
public class Make_PSF implements PlugInFilter {
	protected ImagePlus image;

	// image property members
	private int width;
	private int height;
	private float max = 0;
	private float min = 0;
	private Deconvolve_Image_Utils diu = new Deconvolve_Image_Utils();

	@Override
	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}

		image = imp;
		return DOES_8G | DOES_16 | DOES_32 | DOES_RGB;
	}

	@Override
	public void run(ImageProcessor ip) {
		// get dimensions of image
		width = ip.getWidth();
		height = ip.getHeight();
		
		max = (float)ip.getMax();
		min = (float)ip.getMin();
		
		process(ip);
	}
	
	public void process(ImageProcessor ip) {
		float[][][] pointImageMat = new float[1][height][width];
		boolean expVals = false;
		int pointRad = 1;
		int centerX = (width / 2) - 1;
		int centerY = (height / 2) - 1;
		GenericDialog gd = new GenericDialog("PSF Setup");
		gd.addNumericField("Point radius (px): ", 1, 0);
		gd.addCheckbox("Use experimental values?", expVals);
		gd.showDialog();
		
		pointRad = (int)gd.getNextNumber();
		expVals = gd.getNextBoolean();
		
		for (int i = 0; i < height; i++)
			for (int j = 0; j < width; j++) {
				if (Math.pow(i - centerY, 2) + Math.pow(j - centerX, 2) <= pointRad*pointRad)
					if (expVals)
						pointImageMat[0][i][j] = min;
					else
						pointImageMat[0][i][j] = 0;
				else
					if (expVals)
						pointImageMat[0][i][j] = max;
					else
						pointImageMat[0][i][j] = 255;
			}
				
		ImagePlus pointImage = diu.reassign(pointImageMat, "GRAY32", "Result");
		pointImage.show();
	}
	
	public void showAbout() {
		IJ.showMessage("MakePointImage",
			"Make simple images with custom-sized points in the center."
		);
	}
}