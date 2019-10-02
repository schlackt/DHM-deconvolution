package edu.pdx.imagej.deconv;

import org.jtransforms.fft.FloatFFT_3D;

import ij.IJ;

public class Wiener_Utils {
	
	private Deconvolve_Image_Utils diu = new Deconvolve_Image_Utils();
	private FloatFFT_3D fft3D;
	private float beta;
	private int width;
	private int height;
	private int slices;
	private int frames;
	private float[][][] psfComplex;
	private boolean get_intensity;
	
	public float[][][][] imgComplex;
	public float[][][][] imgPhase;
	public float scale = 1;
	public float error;
	
	// initialize object
	public Wiener_Utils(int i_width, int i_height, int i_slices, int i_frames, float i_beta, boolean intensity) {
		width = i_width;
		height = i_height;
		slices = i_slices;
		frames = i_frames;
		beta = i_beta;
		get_intensity = intensity;
		fft3D = new FloatFFT_3D((long)slices, (long)height, (long)width);
		imgComplex = new float[frames][slices][height][width];
	}
	
	// assumes imgMat and psfMat are not in FFT form. This method deconvolves real data
	public void deconvolve(float[][][][] imgMat, float[][][] psfMat, boolean getError) {
		// put image into FFT form and transform
		for (int i = 0; i < frames; i++) {
			imgComplex[i] = diu.toFFTform(imgMat[i]);
			if (get_intensity)
				imgComplex[i] = diu.matrixOperations(imgComplex[i], imgComplex[i], "multiply");
			fft3D.complexForward(imgComplex[i]);
		}
			
		// do the same with PSF
		psfComplex = diu.scaleMat(psfMat, scale);
		psfComplex = diu.toFFTform(psfComplex);
		if (get_intensity)
			psfComplex = diu.matrixOperations(psfComplex, psfComplex, "multiply");
		fft3D.complexForward(psfComplex);	
		float[][][] psfConj = diu.complexConj(psfComplex);
		
		for (int i = 0; i < frames; i++) {
			// perform deconvolution operations
			imgComplex[i] = diu.matrixOperations(psfConj, imgComplex[i], "multiply");	
			imgComplex[i] = diu.matrixOperations(imgComplex[i], diu.incrementComplex(diu.matrixOperations(psfComplex, psfConj, "multiply"), beta), "divide");
			fft3D.complexInverse(imgComplex[i], true);
			
			// put complex matrices back into real matrices and format image
			imgComplex[i] = diu.getAmplitudeMat(imgComplex[i]);
			imgComplex[i] = diu.formatIFFT(imgComplex[i]);
			diu.linearShift(imgComplex[i], 0, 1);
			IJ.showProgress(i+1, frames);
		}
		
		// return PSF matrix to original state
		fft3D.complexInverse(psfComplex, true);
		psfComplex = diu.getAmplitudeMat(psfComplex);
		
		if (getError)
			error = (float) diu.getError(diu.toFFTform(imgComplex), diu.toFFTform(imgMat), psfMat);
	}
	
	// treats deconvolution with complex numbers
	public void deconvolve(float[][][][] imgAmpMat, float[][][][] imgPhaseMat, float[][][] psfAmpMat, float[][][] psfPhaseMat, boolean getError, String style) {
		imgPhase = new float[frames][slices][height][width];
		
		// construct complex matrices based on form of input data
		for (int i = 0; i < frames; i++) {
			if (style == "Polar")
				imgComplex[i] = diu.toFFTform(imgAmpMat[i], imgPhaseMat[i]);
			else
				imgComplex[i] = diu.toFFTformRect(imgAmpMat[i], imgPhaseMat[i]);
			
			if (get_intensity)
				imgComplex[i] = diu.matrixOperations(imgComplex[i], diu.complexConj(imgComplex[i]), "multiply");
			
			fft3D.complexForward(imgComplex[i]);
		}
		
		// do same for PSF
		if (style == "Polar")
			psfComplex = diu.toFFTform(psfAmpMat, psfPhaseMat);
		else
			psfComplex = diu.toFFTformRect(psfAmpMat, psfPhaseMat);
		
		if (get_intensity)
			psfComplex = diu.matrixOperations(psfComplex, diu.complexConj(psfComplex), "multiply");
		
		fft3D.complexForward(psfComplex);	
		float[][][] psfConj = diu.complexConj(psfComplex);
		
		// same deconvolution procedure as above
		for (int i = 0; i < frames; i++) {
			imgComplex[i] = diu.matrixOperations(psfConj, imgComplex[i], "multiply");	
			imgComplex[i] = diu.matrixOperations(imgComplex[i], diu.incrementComplex(diu.matrixOperations(psfComplex, psfConj, "multiply"), beta), "divide");
			fft3D.complexInverse(imgComplex[i], true);
			
			// put complex matrices back into real matrices and format image
			if (style == "Polar") {
				imgPhase[i] = diu.getPhaseMat(imgComplex[i]);
				imgComplex[i] = diu.getAmplitudeMat(imgComplex[i]);
			}
			else {
				imgPhase[i] = diu.getImMat(imgComplex[i]);
				imgComplex[i] = diu.getReMat(imgComplex[i]);
			}
			imgComplex[i] = diu.formatIFFT(imgComplex[i]);
			imgPhase[i] = diu.formatIFFT(imgPhase[i]);
			IJ.showProgress(i+1, frames);
		}
		
		if (getError)
			if (style == "Polar")
				error = (float) diu.getError(diu.toFFTform(imgComplex, imgPhase), diu.toFFTform(imgAmpMat, imgPhaseMat), diu.toFFTform(psfAmpMat, psfPhaseMat));
			else
				error = (float) diu.getError(diu.toFFTformRect(imgComplex, imgPhase), diu.toFFTformRect(imgAmpMat, imgPhaseMat), diu.toFFTformRect(psfAmpMat, psfPhaseMat));
	}
}