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
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;

/**
 * Plugin for deconvolving images with an assumed Point Spread Function (PSF)
 * or an experimentally determined PSF. The plugin can use an instant or 
 * iterative method to perform the deconvolution.
 *
 * @author Trevor Schlack
 */
public class Make_PSF implements PlugInFilter {
	protected ImagePlus image_ref;

	// image property members
	private int width;
	private int height;
	private double pointRad;
	private boolean expVals;
	private String ref_selection;
	private float max = 0;
	private float min = 0;
	private Deconvolve_Image_Utils diu = new Deconvolve_Image_Utils();

	@Override
	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}
		return DOES_8G | DOES_16 | DOES_32 | DOES_RGB;
	}

	@Override
	public void run(ImageProcessor ip) {		
		if (showDialog())
			process(ip);
	}
	
	public boolean showDialog() {
		String[] image_list = diu.imageList();
		GenericDialog gd = new GenericDialog("PSF Setup");
		gd.addChoice("Reference image: ", image_list, image_list[0]);
		gd.addNumericField("Point radius (px): ", 1, 0);
		gd.addCheckbox("Use experimental values?", expVals);
		gd.showDialog();
		

		if (gd.wasCanceled())
			return false;
		
		ref_selection = gd.getNextChoice();
		pointRad = gd.getNextNumber();
		expVals = gd.getNextBoolean();
		
		// ensure required images are entered
		if (ref_selection == "<none>") {
			IJ.showMessage("Reference image required.");
			return showDialog();
		}
		
		image_ref = WindowManager.getImage(diu.getImageTitle(ref_selection));
		width = image_ref.getProcessor().getWidth();
		height = image_ref.getProcessor().getHeight();
		min = (float) image_ref.getProcessor().getMin();
		max = (float) image_ref.getProcessor().getMax();
		
		return true;
	}
	
	public void process(ImageProcessor ip) {
		float[][] pointImageMat = new float[height][width];	
		int centerX = (width / 2) - 1;
		int centerY = (height / 2) - 1;
		
		for (int i = 0; i < height; i++)
			for (int j = 0; j < width; j++) {
				if (Math.pow(i - centerY, 2) + Math.pow(j - centerX, 2) <= pointRad*pointRad)
					if (expVals)
						pointImageMat[i][j] = min;
					else
						pointImageMat[i][j] = (float) 0.0;
				else
					if (expVals)
						pointImageMat[i][j] = max;
					else
						pointImageMat[i][j] = (float) 255.0;
			}
		
		ImageProcessor ip2 =  new FloatProcessor(pointImageMat);
		ImagePlus pointImage = new ImagePlus("Result", ip2);
		pointImage.show();
	}
	
	public void showAbout() {
		IJ.showMessage("MakePointImage",
			"Make simple images with custom-sized points in the center."
		);
	}
}