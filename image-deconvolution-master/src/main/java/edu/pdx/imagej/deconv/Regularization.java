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

public class Regularization implements PlugInFilter {
	protected ImagePlus image;
	protected ImagePlus PSF;

	private int tildeCount = 1;
	private float smooth;
	private float nonlinearity;
	private int iterations;
	private float lateral_spacing;
	private float axial_spacing;
	private float norm = 255;
	private String path;
	private String choice;
	
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
	
	// Show window for various settings
	private boolean showDialog() {
		String[] choices = {"8-bit", "16-bit", "32-bit"};
		GenericDialog gd = new GenericDialog("Deconvolution Setup");
		gd.addChoice("Output Image:", choices, "32-bit");
		gd.addNumericField("Smoothness Factor: ", 0.01, 2);
		gd.addNumericField("Nonlinearity Factor: ", 0.001, 3);
		gd.addNumericField("# Iterations: ", 3, 0);
		gd.addNumericField("Lateral Spacing (o.u.): ", 0.178223, 3);
		gd.addNumericField("Axial Spacing (o.u.): ", 10, 0);

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		// get entered values
		choice = gd.getNextChoice();
		smooth = (float) gd.getNextNumber();
		nonlinearity = (float) gd.getNextNumber();
		iterations = (int) gd.getNextNumber();
		lateral_spacing = (float) gd.getNextNumber();
		axial_spacing = (float) gd.getNextNumber();
		
		if (choice == "8-bit")
			choice = "GRAY8";
		else if (choice == "16-bit")
			choice = "GRAY16";
		else {
			choice = "GRAY32";
			norm = 1;
		}

		return true;
	}
	
	public void process(ImageProcessor ip) {
		// get the PSF file path or exit if the user presses "Cancel"
		path = diu.getPath("Select the PSF image:");
		if (path == null) {
			return;
		}
		PSF = IJ.openImage(path);
		
		//invert PSF and image so signal takes high values instead of low values
		diu.invert(PSF);
		diu.invert(image);
		
		// convert image stacks to matrices
		float[][][][] ampMat = diu.getMatrix4D(image);
		for (int i = 0; i < ampMat.length; i++)
			diu.linearShift(ampMat[i], 0, 1);
		float[][][] psfMat = diu.getMatrix3D(PSF);
		diu.normalize(psfMat);
		
		// initialize the regularization
		IJ.showStatus("Initializing...");
		Regularization_Utils ru = new Regularization_Utils(ampMat, psfMat, lateral_spacing, axial_spacing, smooth, nonlinearity);
		// deconvolve according to the flow chart in Arigovindan+ 2013 (supplementary information)
		for (int i = 0; i < iterations; i++) {
			IJ.showStatus("Processing iteration " + Integer.toString(i+1) + " of " + Integer.toString(iterations) + "...");
			
			ru.get_dMat();
			ru.get_uMat();
			
			ru.get_guessTilde();
			ru.getEnergyMeasure(true);
			float[] tildes = {ru.errorTilde, ru.errorTilde, ru.errorTilde, ru.errorTilde};
			while (!ru.checkTilde()) {
				IJ.showStatus("Tilde check #" + Integer.toString(tildeCount) + "...");
				ru.damping = (float) (0.7 * ru.damping);
				ru.get_guessTilde();
				ru.getEnergyMeasure(true);
				tildes[tildeCount % 4] = ru.errorTilde;
				if (tildes[0] < tildes[1] && tildes[1] < tildes[2] && tildes[2] < tildes[3]) {
					IJ.showMessage("Error seems to be diverging. Please try again with different parameters.");
					return;
				}
				tildeCount += 1;
			}
			tildeCount = 1;
			
			ru.update();
		}
		
		ru.getAmplitude(norm);
		ImagePlus result = diu.reassign(ru.guess, choice, "Result");
		diu.invert(result);
		diu.invert(image);
		result.show();
	}
	
	public void showAbout() {
		IJ.showMessage("DeconvolveImage",
			"Deconvolves DHM images using entropy regularization."
		);
	}
}