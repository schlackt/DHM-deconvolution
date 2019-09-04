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
	private float[][][] psfCopy;
	
	public float[][][][] imgCopy;
	public float scale = 1;
	public float error;
	
	public Wiener_Utils(int i_width, int i_height, int i_slices, int i_frames, float i_beta) {
		width = i_width;
		height = i_height;
		slices = i_slices;
		frames = i_frames;
		beta = i_beta;
		fft3D = new FloatFFT_3D((long)slices, (long)height, (long)width);
		imgCopy = new float[frames][slices][height][width];
	}
	
	// assumes imgMat and psfMat are not in FFT form
	public void deconvolve(float[][][][] imgMat, float[][][] psfMat, boolean getError) {
		for (int i = 0; i < frames; i++) {
			imgCopy[i] = diu.toFFTform(imgMat[i]);
			fft3D.complexForward(imgCopy[i]);
		}
			
		psfCopy = diu.scaleMat(psfMat, scale);
		psfCopy = diu.toFFTform(psfCopy);
		fft3D.complexForward(psfCopy);	
		float[][][] psfConj = diu.complexConj(psfCopy);
		
		for (int i = 0; i < frames; i++) {
			imgCopy[i] = diu.matrixOperations(psfConj, imgCopy[i], "multiply");	
			imgCopy[i] = diu.matrixOperations(imgCopy[i], diu.incrementComplex(diu.matrixOperations(psfCopy, psfConj, "multiply"), beta), "divide");
			fft3D.complexInverse(imgCopy[i], true);
			
			// put complex matrices back into real matrices and format image
			imgCopy[i] = diu.getAmplitudeMat(imgCopy[i]);
			imgCopy[i] = diu.formatWienerAmp(imgCopy[i]);
			diu.linearShift(imgCopy[i], 0, 1);
			IJ.showProgress(i+1, frames);
		}
		fft3D.complexInverse(psfCopy, true);
		psfCopy = diu.getAmplitudeMat(psfCopy);
		
		if (getError)
			error = (float) diu.getError(imgCopy, imgMat, psfCopy);
	}
}