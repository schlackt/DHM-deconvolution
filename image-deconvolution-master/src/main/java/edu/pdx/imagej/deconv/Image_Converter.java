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
import ij.process.ImageProcessor;

public class Image_Converter implements PlugInFilter {
	protected ImagePlus image_amp;
	protected ImagePlus image_phase;
	private String amp_selection;
	private String phase_selection;
	private String style;
	
	private Deconvolve_Image_Utils diu = new Deconvolve_Image_Utils();

	@Override
	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}

		return DOES_8G | DOES_16 | DOES_32;
	}

	@Override
	public void run(ImageProcessor ip) {
		if (showDialog()) {
			process(ip);
		}
	}
	
	private boolean showDialog() {
		String[] conv_choices = {"Polar -> Rectangular", "Rectangular -> Polar"};
		String[] image_list = diu.imageList();
		GenericDialog gd = new GenericDialog("Conversion Setup");
		gd.addChoice("Deconvolution Style: ", conv_choices, "Rectangular -> Polar");
		gd.addChoice("Amplitude/Real image: ", image_list, image_list[image_list.length - 1]);
		gd.addChoice("Phase/Imaginary image: ", image_list, image_list[image_list.length - 1]);
		
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		
		style = gd.getNextChoice();
		amp_selection = gd.getNextChoice();
		phase_selection = gd.getNextChoice();
		
		// ensure required images are entered
		if (amp_selection == "<none>" || phase_selection == "<none>") {
			IJ.showMessage("Two images are required for conversion.");
			return showDialog();
		}

		
		return true;
	}
	
	public void process(ImageProcessor ip) {
		image_amp = WindowManager.getImage(diu.getImageTitle(amp_selection));
		image_phase = WindowManager.getImage(diu.getImageTitle(phase_selection));
		
		
		// convert image stacks to matrices
		float[][][][] imgMat = diu.getMatrix4D(image_amp);
		float[][][][] imgMatPhase = diu.getMatrix4D(image_phase);
		

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