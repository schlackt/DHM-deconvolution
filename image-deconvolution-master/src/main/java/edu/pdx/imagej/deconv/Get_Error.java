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
import java.text.DecimalFormat;


public class Get_Error implements PlugInFilter {
	protected ImagePlus image;
	protected ImagePlus PSF;
	protected ImagePlus origImage;
	private String path;
	private String style;
	
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
		if (showDialog()) {
			process(ip);
			image.updateAndDraw();
		}
	}
	
	private boolean showDialog() {
		String[] decon_choices = {"Standard", "Complex (Polar)", "Complex (Rectangular)"};
		GenericDialog gd = new GenericDialog("Error Setup");
		gd.addChoice("Deconvolution Style: ", decon_choices, "Standard");
		
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		
		style = gd.getNextChoice();
		return true;
	}
	
	public void process(ImageProcessor ip) {
		// get the PSF file path or exit if the user presses "Cancel"
		path = diu.getPath("Select the real/amplitude PSF image:");
		if (path == null) {
			return;
		}
		PSF = IJ.openImage(path);
		
		path = diu.getPath("Select the original real/amplitude image:");
		if (path == null) {
			return;
		}
		origImage = IJ.openImage(path);
		
		// convert image stacks to matrices
		float[][][][] imgMat = diu.getMatrix4D(image);
		float[][][][] imgMatOld = diu.getMatrix4D(origImage);
		float[][][] psfMat = diu.getMatrix3D(PSF);
		
		double err = 0;
		if (style == "Standard")
			err = diu.getError(diu.toFFTform(imgMat), diu.toFFTform(imgMatOld), diu.toFFTform(psfMat));
		else {
			path = diu.getPath("Select the deconvolved imaginary/phase image:");
			origImage = IJ.openImage(path);
			float[][][][] imgMatPhase = diu.getMatrix4D(origImage);
			
			path = diu.getPath("Select the imaginary/phase PSF image:");
			PSF = IJ.openImage(path);
			float[][][] psfPhase = diu.getMatrix3D(PSF);
			
			path = diu.getPath("Select the original imaginary/phase image:");
			origImage = IJ.openImage(path);
			float[][][][] imgMatOldPhase = diu.getMatrix4D(origImage);
			
			if (style == "Complex (Polar)")
				err = diu.getError(diu.toFFTform(imgMat, imgMatPhase), diu.toFFTform(imgMatOld, imgMatOldPhase), diu.toFFTform(psfMat, psfPhase));
			else
				err = diu.getError(diu.toFFTformRect(imgMat, imgMatPhase), diu.toFFTformRect(imgMatOld, imgMatOldPhase), diu.toFFTformRect(psfMat, psfPhase));
		}
		DecimalFormat errFormat = new DecimalFormat("###0.00");
		IJ.showMessage("Error: " + errFormat.format(err * 100) + "%");
	}
	
	public void showAbout() {
		IJ.showMessage("DeconvolveImage",
			"Deconvolves DHM images using the Wiener filter."
		);
	}
}