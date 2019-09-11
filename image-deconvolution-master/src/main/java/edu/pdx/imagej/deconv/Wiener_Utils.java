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
	
	public float[][][][] imgComplex;
	public float[][][][] imgPhase;
	public float scale = 1;
	public float error;
	
	public Wiener_Utils(int i_width, int i_height, int i_slices, int i_frames, float i_beta) {
		width = i_width;
		height = i_height;
		slices = i_slices;
		frames = i_frames;
		beta = i_beta;
		fft3D = new FloatFFT_3D((long)slices, (long)height, (long)width);
		imgComplex = new float[frames][slices][height][width];
	}
	
	// assumes imgMat and psfMat are not in FFT form
	public void deconvolve(float[][][][] imgMat, float[][][] psfMat, boolean getError) {
		for (int i = 0; i < frames; i++) {
			imgComplex[i] = diu.toFFTform(imgMat[i]);
			fft3D.complexForward(imgComplex[i]);
		}
			
		psfComplex = diu.scaleMat(psfMat, scale);
		psfComplex = diu.toFFTform(psfComplex);
		fft3D.complexForward(psfComplex);	
		float[][][] psfConj = diu.complexConj(psfComplex);
		
		for (int i = 0; i < frames; i++) {
			imgComplex[i] = diu.matrixOperations(psfConj, imgComplex[i], "multiply");	
			imgComplex[i] = diu.matrixOperations(imgComplex[i], diu.incrementComplex(diu.matrixOperations(psfComplex, psfConj, "multiply"), beta), "divide");
			fft3D.complexInverse(imgComplex[i], true);
			
			// put complex matrices back into real matrices and format image
			imgComplex[i] = diu.getAmplitudeMat(imgComplex[i]);
			imgComplex[i] = diu.formatWienerAmp(imgComplex[i]);
			diu.linearShift(imgComplex[i], 0, 1);
			IJ.showProgress(i+1, frames);
		}
		fft3D.complexInverse(psfComplex, true);
		psfComplex = diu.getAmplitudeMat(psfComplex);
		
		if (getError)
			error = (float) diu.getError(imgComplex, imgMat, psfComplex);
	}
	
	// treats complex numbers
		public void deconvolve(float[][][][] imgAmpMat, float[][][][] imgPhaseMat, float[][][] psfAmpMat, float[][][] psfPhaseMat, boolean getError, String style) {
			for (int i = 0; i < frames; i++) {
				if (style == "Polar")
					imgComplex[i] = diu.toFFTform(imgAmpMat[i], imgPhaseMat[i]);
				else
					imgComplex[i] = diu.toFFTformRect(imgAmpMat[i], imgPhaseMat[i]);
				
				fft3D.complexForward(imgComplex[i]);
			}
			
			if (style == "Polar")
				psfComplex = diu.toFFTform(psfAmpMat, psfPhaseMat);
			else
				psfComplex = diu.toFFTformRect(psfAmpMat, psfPhaseMat);
			
			fft3D.complexForward(psfComplex);	
			float[][][] psfConj = diu.complexConj(psfComplex);
			
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
				imgComplex[i] = diu.formatWienerAmp(imgComplex[i]);
				imgPhase[i] = diu.formatWienerAmp(imgPhase[i]);
				diu.linearShift(imgComplex[i], 0, 1);
				IJ.showProgress(i+1, frames);
			}
			fft3D.complexInverse(psfComplex, true);
			psfComplex = diu.getAmplitudeMat(psfComplex);
			
			if (getError)
				error = (float) diu.getError(imgComplex, imgAmpMat, psfComplex);
		}
}