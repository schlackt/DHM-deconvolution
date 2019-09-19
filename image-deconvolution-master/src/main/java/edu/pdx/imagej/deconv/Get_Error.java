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
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import java.text.DecimalFormat;


public class Get_Error implements PlugInFilter {
	protected ImagePlus image;
	protected ImagePlus PSF;
	protected ImagePlus origImage;
	private String path;
	
	private Deconvolve_Image_Utils diu = new Deconvolve_Image_Utils();

	@Override
	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}

		image = imp;
		return DOES_8G | DOES_16 | DOES_32;
	}

	@Override
	public void run(ImageProcessor ip) {	
		process(ip);
		image.updateAndDraw();
	}
	
	
	public void process(ImageProcessor ip) {
		// get the PSF file path or exit if the user presses "Cancel"
		path = diu.getPath("Select the PSF image:");
		if (path == null) {
			return;
		}
		PSF = IJ.openImage(path);
		
		path = diu.getPath("Select the original blurred image:");
		if (path == null) {
			return;
		}
		origImage = IJ.openImage(path);
		
		//invert PSF and image so signal takes high values instead of low values
		diu.invert(PSF);
		diu.invert(image);
		diu.invert(origImage);
		
		// convert image stacks to matrices
		float[][][][] imgMat = diu.getMatrix4D(image);
		float[][][][] imgMatOld = diu.getMatrix4D(origImage);
		float[][][] psfMat = diu.getMatrix3D(PSF);
		
		double err = diu.getError(diu.toFFTform(imgMat), diu.toFFTform(imgMatOld), diu.toFFTform(psfMat));
		DecimalFormat errFormat = new DecimalFormat("###0.00");
		IJ.showMessage("Error: " + errFormat.format(err * 100) + "%");
		diu.invert(image);
	}
	
	public void showAbout() {
		IJ.showMessage("DeconvolveImage",
			"Deconvolves DHM images using the Wiener filter."
		);
	}
}