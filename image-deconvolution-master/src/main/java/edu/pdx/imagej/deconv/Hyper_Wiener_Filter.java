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
import org.jtransforms.fft.FloatFFT_3D;

public class Hyper_Wiener_Filter implements PlugInFilter {
	protected ImagePlus image;
	protected ImagePlus PSF;

	private int width;
	private int height;
	private int slices;
	private int frames;
	private int norm = 255;
	private String path;
	private String choice;
	private boolean getSNR;
	private float SNR;
	
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
		gd.addCheckbox("SNR?", true);

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		// get entered values
		choice = gd.getNextChoice();
		getSNR = gd.getNextBoolean();
		
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
		
		if (getSNR) {
			// get signal-to-noise through user input
			Noise_NP nnp = new Noise_NP();
			float noiseDev = nnp.getNoise(image);
			float signal = nnp.getSignal(image);
			SNR = signal / noiseDev;
		}
		
		IJ.showStatus("Preprocessing...");
		
		// convert image stacks to matrices
		float[][][][] ampMat = diu.getMatrix4D(image);
		float[][][] psfMat = diu.getMatrix3D(PSF);
		diu.normalize(psfMat);
		
		// put matrices into proper format for FFT (even columns are Real, odd columns are imaginary)
		for (int i = 0; i < frames; i++) {
			diu.linearShift(ampMat[i], 0, 1);
			ampMat[i] = diu.toFFTform(ampMat[i]);
			IJ.showProgress(i+1, 3*frames + 3);
		}	
		psfMat = diu.toFFTform(psfMat);
		FloatFFT_3D fft3D = new FloatFFT_3D((long)slices, (long)height, (long)width);
		IJ.showProgress(frames+1, 3*frames + 3);
		
		// carry out FFT
		for (int i = 0; i < frames; i++) {
			fft3D.complexForward(ampMat[i]);
			IJ.showProgress(frames+i+2, 3*frames + 3);
		}
		fft3D.complexForward(psfMat);
		float[][][] psfConj = diu.complexConj(psfMat);
		IJ.showProgress(2*frames + 2, 3*frames + 3);
				
		// now perform deconvolution operations, storing the result in ampMat
		for (int i = 0; i < frames; i++) {
			IJ.showStatus("Processing frame " + Integer.toString(i+1) + " of " + Integer.toString(frames) + "...");
			ampMat[i] = diu.matrixOperations(psfConj, ampMat[i], "multiply");	
			ampMat[i] = diu.matrixOperations(ampMat[i], diu.incrementComplex(diu.matrixOperations(psfMat, psfConj, "multiply"), 1/SNR), "divide");
			fft3D.complexInverse(ampMat[i], true);
			
			// put complex matrices back into real matrices and format image
			ampMat[i] = diu.getAmplitudeMat(ampMat[i]);
			ampMat[i] = diu.formatWienerAmp(ampMat[i]);
			// normalize image based on desired output type
			diu.linearShift(ampMat[i], 0, norm);
			IJ.showProgress(2*frames + i + 3, 3*frames + 3);
		}

		// store results in a new ImagePlus image and display it
		IJ.showStatus("Wrapping up...");
		ImagePlus ampImage = diu.reassign(ampMat, choice, "Result");
		diu.invert(ampImage);
		diu.invert(image);
		ampImage.show();
		IJ.showProgress(3*frames + 3, 3*frames + 3);
	}
	
	public void showAbout() {
		IJ.showMessage("DeconvolveImage",
			"Deconvolves DHM images using the Wiener filter."
		);
	}
}