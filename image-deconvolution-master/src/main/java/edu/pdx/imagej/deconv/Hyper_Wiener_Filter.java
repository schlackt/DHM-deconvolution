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

public class Hyper_Wiener_Filter implements PlugInFilter {
	protected ImagePlus image;
	protected ImagePlus PSF;

	private int width;
	private int height;
	private int slices;
	private int frames;
	private String path;
	private String choice;
	private boolean getSNR;
	private boolean normalizePSF;
	private boolean do_minimization;
	private boolean do_inversion;
	private boolean get_error;
	private float SNR;
	private float[][][][] ampMat;
	private float[][][] psfMat;
	
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
		// get dimensions of image
		width = ip.getWidth();
		height = ip.getHeight();
		slices = image.getNSlices();
		frames = image.getNFrames();
		
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
		gd.addCheckbox("Invert Images?", true);
		gd.addCheckbox("Get SNR?", true);
		gd.addCheckbox("Normalize PSF?", true);
		gd.addCheckbox("Minimize Error?", true);
		gd.addCheckbox("Display Error?", true);

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		// get entered values
		choice = gd.getNextChoice();
		do_inversion = gd.getNextBoolean();
		getSNR = gd.getNextBoolean();
		normalizePSF = gd.getNextBoolean();
		do_minimization = gd.getNextBoolean();
		get_error = gd.getNextBoolean();
		
		if (!getSNR) {
			GenericDialog gd2 = new GenericDialog("Custom Beta");
			gd2.addNumericField("Beta:", 0.001, 3);
			
			gd2.showDialog();
			if (gd2.wasCanceled())
				return false;
			
			SNR = (float) (1 / gd2.getNextNumber());
		}
		
		if (choice == "8-bit")
			choice = "GRAY8";
		else if (choice == "16-bit")
			choice = "GRAY16";
		else {
			choice = "GRAY32";
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
		
		if (do_inversion) {
			diu.invert(image);
			diu.invert(PSF);
		}
		
		if (getSNR) {
			// get signal-to-noise through user input
			Noise_NP nnp = new Noise_NP();
			float noiseDev = nnp.getNoise(image);
			float signal = nnp.getSignal(image);
			SNR = signal / noiseDev;
		}
		
		IJ.showStatus("Preprocessing...");
		
		// convert image stacks to matrices
		ampMat = diu.getMatrix4D(image);
		psfMat = diu.getMatrix3D(PSF);
		
		if (normalizePSF)
			diu.normalize(psfMat);
		
		Wiener_Utils wu = new Wiener_Utils(width, height, slices, frames, 1/SNR);
		
		if (do_minimization)
			wu.scale = bisect(wu, (float) (1 / 255 / height / width), 1, 1);
		
		wu.deconvolve(ampMat, psfMat, get_error);

		// store results in a new ImagePlus image and display it
		IJ.showStatus("Wrapping up...");
		ImagePlus ampImage = diu.reassign(wu.imgCopy, choice, "Result");
		if (do_inversion) {
			diu.invert(image);
			diu.invert(ampImage);
		}
		ampImage.show();
		if (get_error)
			IJ.showMessage("Error: " + Float.toString(wu.error) + "%");
	}
	
	private float bisect(Wiener_Utils wu, float scale_left, float scale_right, float tol) {
		float scale_mid = (float) (0.5 * (scale_left + scale_right));
		wu.scale = scale_left;
		wu.deconvolve(ampMat, psfMat, true);
		float err_left = wu.error;
		
		wu.scale = scale_mid;
		wu.deconvolve(ampMat, psfMat, true);
		float err_mid = wu.error;
		
		wu.scale = scale_right;
		wu.deconvolve(ampMat, psfMat, true);
		float err_right = wu.error;
		
		if (Math.abs(err_right - err_left) <= tol)
			return scale_mid;
		else if (err_left < err_mid)
			return bisect(wu, scale_left, scale_mid, tol);
		else if (err_mid < err_right && !(err_mid < err_left))
			return bisect(wu, scale_mid, scale_right, tol);
		else
			return bisect(wu, (float) (0.5 * (scale_left + scale_mid)), (float) (0.5 * (scale_mid + scale_right)), tol);
	}
	
	public void showAbout() {
		IJ.showMessage("DeconvolveImage",
			"Deconvolves DHM images using the Wiener filter."
		);
	}
}