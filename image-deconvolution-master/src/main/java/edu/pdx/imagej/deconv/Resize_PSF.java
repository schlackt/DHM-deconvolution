/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package edu.pdx.imagej.deconv;

import java.util.Arrays;
import java.util.Random;

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
public class Resize_PSF implements PlugInFilter {
	protected ImagePlus image;

	// image property members
	private int width;
	private int height;
	private int slices;
	private int new_height;
	private int new_width;
	private int startX;
	private int startY;
	private int randInt;
	private boolean fill;
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
		slices = image.getNSlices();
		
		if (showDialog())
			process(ip);
	}
	
	private boolean showDialog() {
		GenericDialog gd = new GenericDialog("PSF Setup");
		gd.addNumericField("New Height (px):", 2048, 0);
		gd.addNumericField("New Width (px):", 2048, 0);
		gd.addCheckbox("Fill Randomly?", false);
		
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		
		new_height = (int) gd.getNextNumber();
		new_width = (int) gd.getNextNumber();
		fill = gd.getNextBoolean();
		
		return true;
	}
	
	public void process(ImageProcessor ip) {
		float[][][] newPSF = new float[slices][new_height][new_width];
		float[][][] oldPSF = new float[slices][height][width];
		float[] border = new float[2*width + 2*height];
		float median;
		Random rand = new Random();
		oldPSF = diu.getMatrix3D(image);
		startX = (new_width - width) / 2;
		startY = (new_height - width) / 2;
		
		for (int i = 0; i < slices; i++) {
			for (int j = startY; j < startY + height; j++)
				for (int k = startX; k < startX + width; k++)
					newPSF[i][j][k] = oldPSF[i][j - startY][k - startX];
		
			for (int j = 0; j < width; j++)
				border[j] = oldPSF[i][0][j];
			for (int j = 0; j < width; j++)
				border[width + j] = oldPSF[i][height - 1][j];
			for (int j = 0; j < height; j++)
				border[2*width + j] = oldPSF[i][j][0];
			for (int j = 0; j < height; j++)
				border[2*width + height + j] = oldPSF[i][j][width - 1];
			
			Arrays.sort(border);
			median = ( border[border.length/2] + border[(border.length/2)-1] ) / 2;
			
			for (int j = 0; j < new_height; j++)
				for (int k = 0; k < new_width; k++) {
					if (j < startY || j >= startY + height || k < startX || k >= startX + width) {
						if (fill) {
							randInt = rand.nextInt(2*width + 2*height);
							newPSF[i][j][k] = border[randInt];
						}
						else
							newPSF[i][j][k] = median;
					}
				}
		}
			
		ImagePlus result = diu.reassign(newPSF, "GRAY32", "ResizedPSF");
		result.show();
	}
	
	public void showAbout() {
		IJ.showMessage("MakePointImage",
			"Make simple images with custom-sized points in the center."
		);
	}
}