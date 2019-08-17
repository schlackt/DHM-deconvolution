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

	private int width;
	private int height;
	private int slices;
	private int frames;
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
		// get dimensions of image
		width = ip.getWidth();
		height = ip.getHeight();
		slices = image.getNSlices();
		frames = image.getNFrames();
	
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
		
		double err = getError(imgMat, imgMatOld, psfMat);
		DecimalFormat errFormat = new DecimalFormat("###0.00");
		IJ.showMessage("Error: " + errFormat.format(err) + "%");
		diu.invert(image);
	}
	
	private double getError(float[][][][] ampApprox, float[][][][] amp, float[][][] psfMat) {
		int zeroCount = 0;
		double ampError = 0;
		float[][][] ampConv;
		diu.normalizePSF(psfMat);
		for (int l = 0; l < frames; l++) {
			diu.normalize(ampApprox[l], 0, 1);
			diu.normalize(amp[l], 0, 1);
			ampConv = diu.getAmplitudeMat(diu.fourierConvolve(diu.toFFTform(ampApprox[l]), diu.toFFTform(psfMat)));
			diu.normalize(ampConv, 0, 1);
			for (int i = 0; i < slices; i++)
				for (int j = 0; j < height; j++)
					for (int k = 0; k < width; k++) {
						if (amp[l][i][j][k] != 0)
							ampError += (double)Math.abs(Math.abs(ampConv[i][j][k]) - Math.abs(amp[l][i][j][k])) / (double)Math.abs(amp[l][i][j][k]);
						else
							zeroCount += 1;
				}
		}
		return 100*Math.abs(ampError / (frames*slices*height*width - zeroCount));
	}
	
	public void showAbout() {
		IJ.showMessage("DeconvolveImage",
			"Deconvolves DHM images using the Wiener filter."
		);
	}
}