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

public class Image_Converter implements PlugInFilter {
	protected ImagePlus image;
	protected ImagePlus imagePhase;
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
		String[] conv_choices = {"Polar -> Rectangular", "Rectangular -> Polar"};
		GenericDialog gd = new GenericDialog("Conversion Setup");
		gd.addChoice("Deconvolution Style: ", conv_choices, "Rectangular -> Polar");
		
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		
		style = gd.getNextChoice();
		return true;
	}
	
	public void process(ImageProcessor ip) {
		// get the PSF file path or exit if the user presses "Cancel"
		path = diu.getPath("Select the imaginary/phase image:");
		if (path == null) {
			return;
		}
		imagePhase = IJ.openImage(path);
		
		
		// convert image stacks to matrices
		float[][][][] imgMat = diu.getMatrix4D(image);
		float[][][][] imgMatPhase = diu.getMatrix4D(imagePhase);
		

		if (style == "Rectangular -> Polar") {
			float[][][][] complexForm = diu.toFFTformRect(imgMat, imgMatPhase);
			float[][][][] ampMat = diu.getAmplitudeMat(complexForm);
			float[][][][] phaseMat = diu.getPhaseMat(complexForm);
			
			ImagePlus ampImage = diu.reassign(ampMat, "GRAY32", "Amplitude");
			ampImage.show();
			ImagePlus phaseImage = diu.reassign(phaseMat, "GRAY32", "Phase");
			phaseImage.show();
		}
		else {
			float[][][][] complexForm = diu.toFFTform(imgMat, imgMatPhase);
			float[][][][] ampMat = diu.getReMat(complexForm);
			float[][][][] phaseMat = diu.getImMat(complexForm);
			
			ImagePlus realImage = diu.reassign(ampMat, "GRAY32", "Real");
			realImage.show();
			ImagePlus imagImage = diu.reassign(phaseMat, "GRAY32", "Imaginary");
			imagImage.show();
		}
	}
	
	public void showAbout() {
		IJ.showMessage("DeconvolveImage",
			"Deconvolves DHM images using the Wiener filter."
		);
	}
}