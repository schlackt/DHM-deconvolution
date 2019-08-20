package edu.pdx.imagej.deconv;

import org.jtransforms.fft.FloatFFT_3D;

public class Regularization_Utils {
	
	private Deconvolve_Image_Utils diu = new Deconvolve_Image_Utils();
	private FloatFFT_3D fft3D;
	private int width;
	private int height;
	private int slices;
	private int frames;
	private float smooth;
	private float nonlinearity;
	private float dx;
	private float dz;
	private float wx;
	private float wy;
	private float wz;
	private float spacing_ratio;
	private float H0 = 0;
	
	private float[][][] L1;
	private float[][][] L2;
	private float[][][] L3;
	private float[][][] L4;
	private float[][][] L5;
	private float[][][] L6;
	private float[][][] identityMat;
	private float[][][] psfMat;
	private float[][][] pMatFT;
	private float[][][] piMatFT;
	private float[][][][] imgMat;
	private float[][][][] wMat;
	private float[][][][] energyMeasure;
	private float[][][][] nPrime;
	private float[][][][] dMat;
	private float[][][][] uMat;
	private float[][][][] guessTilde;
	private float[][][][] energyMeasureTilde;
	private float[][][][] wMatTilde;
	private float[][][][] nPrimeTilde;
	
	public float damping = (float) 0.8;
	public float error;
	public float errorTilde;
	public float[][][][] guess;
	
	// mass initialization
	public Regularization_Utils(float[][][][] image_mat, float[][][] psf_mat, float img_dx, float img_dz, float smooth_p, float nonlinearity_p) {
		imgMat = image_mat;
		psfMat = psf_mat;
		width = imgMat[0][0][0].length;
		height = imgMat[0][0].length;
		slices = imgMat[0].length;
		frames = imgMat.length;
		dx = img_dx;
		dz = img_dz;
		spacing_ratio = dx / dz;
		smooth = smooth_p;
		nonlinearity = nonlinearity_p;
		fft3D = new FloatFFT_3D((long)slices, (long)height, (long)width);
		
		L1 = new float[slices][height][2*width];
		L2 = new float[slices][height][2*width];
		L3 = new float[slices][height][2*width];
		L4 = new float[slices][height][2*width];
		L5 = new float[slices][height][2*width];
		L6 = new float[slices][height][2*width];
		identityMat = new float[slices][height][2*width];
		wMat = new float[frames][slices][height][2*width];
		dMat = new float[frames][slices][height][2*width];
		uMat = new float[frames][slices][height][2*width];
		guessTilde = new float[frames][slices][height][2*width];
		energyMeasureTilde = new float[frames][slices][height][2*width];
		wMatTilde = new float[frames][slices][height][2*width];
		nPrimeTilde = new float[frames][slices][height][2*width];
		
		guess = new float[frames][slices][height][2*width];
		energyMeasure = new float[frames][slices][height][2*width];
		nPrime = new float[frames][slices][height][2*width];
		pMatFT = new float[slices][height][2*width];
		piMatFT = new float[slices][height][2*width];
		
		psfMat = diu.toFFTform(psfMat);
		for (int i = 0; i < frames; i++)
			imgMat[i] = diu.toFFTform(imgMat[i]);
		
		for (int i = 0; i < slices; i++)
			for (int j = 0; j < height; j++)
				for (int k = 0; k < width; k++) {
					H0 += psfMat[i][j][2*k] * psfMat[i][j][2*k];
					
					// spatial frequencies
					wx = (float) (2 * Math.PI * (k / (width - 1) - 0.5) / dx);
					wy = (float) (2 * Math.PI * (j / (height - 1) - 0.5) / dx);
					wz = (float) (2 * Math.PI * (i / (slices - 1) - 0.5) / dz);
					
					identityMat[i][j][2*k] = 1;
					identityMat[i][j][2*k + 1] = 0;
					
					// L1 - L6 are the filters discussed in Arigovindan+ 2013 (supplementary information)
					L1[i][j][2*k] = (float) (2 * Math.cos(wx) - 2);
					L1[i][j][2*k + 1] = 0;
					
					L2[i][j][2*k] = (float) (2 * Math.cos(wy) - 2);
					L2[i][j][2*k + 1] = 0;
					
					L3[i][j][2*k] = (float) (spacing_ratio * spacing_ratio * (2 * Math.cos(wz) - 2));
					L3[i][j][2*k + 1] = 0;
					
					L4[i][j][2*k] = (float) (Math.sqrt(2) * (1 - Math.cos(wx) - Math.cos(wy) + Math.cos(wx + wy)));
					L4[i][j][2*k + 1] = (float) (Math.sqrt(2) * (Math.sin(wx) + Math.sin(wy) - Math.sin(wx + wy)));
					
					L5[i][j][2*k] = (float) (Math.sqrt(2) * spacing_ratio * (1 - Math.cos(wy) - Math.cos(wz) + Math.cos(wy + wz)));
					L5[i][j][2*k + 1] = (float) (Math.sqrt(2) * spacing_ratio * (Math.sin(wy) + Math.sin(wz) - Math.sin(wy + wz)));
					
					L6[i][j][2*k] = (float) (Math.sqrt(2) * spacing_ratio * (1 - Math.cos(wx) - Math.cos(wz) + Math.cos(wx + wz)));
					L6[i][j][2*k + 1] = (float) (Math.sqrt(2) * spacing_ratio * (Math.sin(wx) + Math.sin(wz) - Math.sin(wx + wz)));
				}
		
		// initialize the P matrix, P_I matrix, and guess according to the paper
		// psfMat and imgMat are both out of Fourier space after these calls.
		initializePmatFT();
		initializeGuess();
		
		// get filters out of Fourier space
		fft3D.complexInverse(L1, true);
		fft3D.complexInverse(L2, true);
		fft3D.complexInverse(L3, true);
		fft3D.complexInverse(L4, true);
		fft3D.complexInverse(L5, true);
		fft3D.complexInverse(L6, true);

		getEnergyMeasure(false);
	}
	
	private void initializePmatFT() {
		fft3D.complexForward(psfMat);
		float[][][] auxiliaryMat = new float[slices][height][2*width];
		for (int i = 0; i < slices; i++)
			for (int j = 0; j < height; j++)
				for (int k = 0; k < width; k++) {
					auxiliaryMat[i][j][2*k] = 1;
					auxiliaryMat[i][j][2*k + 1] = 0;
				}
		auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.matrixOperations(diu.complexConj(L1), L1, "multiply"), "add");
		auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.matrixOperations(diu.complexConj(L2), L2, "multiply"), "add");
		auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.matrixOperations(diu.complexConj(L3), L3, "multiply"), "add");
		auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.matrixOperations(diu.complexConj(L4), L4, "multiply"), "add");
		auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.matrixOperations(diu.complexConj(L5), L5, "multiply"), "add");
		auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.matrixOperations(diu.complexConj(L6), L6, "multiply"), "add");
		
		pMatFT = diu.matrixOperations(diu.complexConj(psfMat), psfMat, "multiply");
		pMatFT = diu.matrixOperations(pMatFT, diu.scaleMat(auxiliaryMat, smooth), "add");
		
		float[] sqrt;
		for (int i = 0; i < slices; i++)
			for (int j = 0; j < height; j++)
				for (int k = 0; k < width; k++) {
					sqrt = sqrtComplex(pMatFT[i][j][2*k], pMatFT[i][j][2*k + 1]);
					piMatFT[i][j][2*k] = sqrt[0];
					piMatFT[i][j][2*k + 1] = sqrt[1];
				}
		piMatFT = diu.matrixOperations(identityMat, piMatFT, "divide");
		fft3D.complexInverse(piMatFT, true);
	}
	
	// get g0
	private void initializeGuess() {
		for (int i = 0; i < frames; i++) {
			fft3D.complexForward(imgMat[i]);
			guess[i] = diu.matrixOperations(identityMat, pMatFT, "divide");
			guess[i] = diu.matrixOperations(guess[i], diu.complexConj(psfMat), "multiply");
			guess[i] = diu.matrixOperations(guess[i], imgMat[i], "multiply");
			fft3D.complexInverse(guess[i], true);
			fft3D.complexInverse(imgMat[i], true);
		}
		fft3D.complexInverse(psfMat, true);
	}
	
	// get N' matrix if tilde is false or N'(~) matrix if tilde is true
	private void get_nPrime(boolean tilde) {
		for (int i = 0; i < frames; i++)
			for (int j = 0; j < slices; j++)
				for (int k = 0; k < height; k++)
					for (int l = 0; l < width; l++) {
						if (tilde) {
							nPrimeTilde[i][j][k][2*l] = 0;
							nPrimeTilde[i][j][k][2*l + 1] = 0;
							if (guessTilde[i][j][k][2*l] < 0)
								nPrimeTilde[i][j][k][2*l] = 1;
						}
						else {
							nPrime[i][j][k][2*l] = 0;
							nPrime[i][j][k][2*l + 1] = 0;
							if (guess[i][j][k][2*l] < 0)
								nPrime[i][j][k][2*l] = 1;
						}
					}
	}
	
	// get W matrix if tilde is false or W(~) matrix if tilde is true
	private void get_wMat(boolean tilde) {
		float[][][] auxiliaryMat;
		for (int i = 0; i < frames; i++) {
			if (tilde) {
				wMatTilde[i] = diu.matrixOperations(guessTilde[i], guessTilde[i], "multiply");
				
				auxiliaryMat = diu.fourierConvolve(L1, guessTilde[i]);
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, auxiliaryMat, "multiply");
				wMatTilde[i] = diu.matrixOperations(wMatTilde[i], auxiliaryMat, "add");
				
				auxiliaryMat = diu.fourierConvolve(L2, guessTilde[i]);
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, auxiliaryMat, "multiply");
				wMatTilde[i] = diu.matrixOperations(wMatTilde[i], auxiliaryMat, "add");
				
				auxiliaryMat = diu.fourierConvolve(L3, guessTilde[i]);
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, auxiliaryMat, "multiply");
				wMatTilde[i] = diu.matrixOperations(wMatTilde[i], auxiliaryMat, "add");
				
				auxiliaryMat = diu.fourierConvolve(L4, guessTilde[i]);
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, auxiliaryMat, "multiply");
				wMatTilde[i] = diu.matrixOperations(wMatTilde[i], auxiliaryMat, "add");
				
				auxiliaryMat = diu.fourierConvolve(L5, guessTilde[i]);
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, auxiliaryMat, "multiply");
				wMatTilde[i] = diu.matrixOperations(wMatTilde[i], auxiliaryMat, "add");
				
				auxiliaryMat = diu.fourierConvolve(L6, guessTilde[i]);
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, auxiliaryMat, "multiply");
				wMatTilde[i] = diu.matrixOperations(wMatTilde[i], auxiliaryMat, "add");
				
				wMatTilde[i] = diu.incrementComplex(wMatTilde[i], nonlinearity);
				wMatTilde[i] = diu.matrixOperations(identityMat, wMatTilde[i], "divide");
			}
			else {
				wMat[i] = diu.matrixOperations(guess[i], guess[i], "multiply");
			
				auxiliaryMat = diu.fourierConvolve(L1, guess[i]);
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, auxiliaryMat, "multiply");
				wMat[i] = diu.matrixOperations(wMat[i], auxiliaryMat, "add");
			
				auxiliaryMat = diu.fourierConvolve(L2, guess[i]);
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, auxiliaryMat, "multiply");
				wMat[i] = diu.matrixOperations(wMat[i], auxiliaryMat, "add");
			
				auxiliaryMat = diu.fourierConvolve(L3, guess[i]);
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, auxiliaryMat, "multiply");
				wMat[i] = diu.matrixOperations(wMat[i], auxiliaryMat, "add");
			
				auxiliaryMat = diu.fourierConvolve(L4, guess[i]);
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, auxiliaryMat, "multiply");
				wMat[i] = diu.matrixOperations(wMat[i], auxiliaryMat, "add");
			
				auxiliaryMat = diu.fourierConvolve(L5, guess[i]);
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, auxiliaryMat, "multiply");
				wMat[i] = diu.matrixOperations(wMat[i], auxiliaryMat, "add");
			
				auxiliaryMat = diu.fourierConvolve(L6, guess[i]);
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, auxiliaryMat, "multiply");
				wMat[i] = diu.matrixOperations(wMat[i], auxiliaryMat, "add");
			
				wMat[i] = diu.incrementComplex(wMat[i], nonlinearity);
				wMat[i] = diu.matrixOperations(identityMat, wMat[i], "divide");
			}
		}	
	}
	
	// get e if tilde is false or e(~) if tilde is true
	private void get_error(boolean tilde) {
		if (tilde)
			errorTilde = 0;
		else
			error = 0;
		
		for (int i = 0; i < frames; i++)
			for (int j = 0; j < slices; j++)
				for (int k = 0; k < height; k++)
					for (int l = 0; l < width; l++) {
						if (tilde)
							errorTilde += energyMeasureTilde[i][j][k][2*l] * energyMeasureTilde[i][j][k][2*l] + energyMeasureTilde[i][j][k][2*l + 1] * energyMeasureTilde[i][j][k][2*l + 1];
						else
							error += energyMeasure[i][j][k][2*l] * energyMeasure[i][j][k][2*l] + energyMeasure[i][j][k][2*l + 1] * energyMeasure[i][j][k][2*l + 1];
					}
	}
	
	// get R matrix if tilde is false, R(~) matrix if tilde is true
	public void getEnergyMeasure(boolean tilde) {
		get_wMat(tilde);
		get_nPrime(tilde);
		float[][][] auxiliaryMat;
		
		for (int i = 0; i < frames; i++) {
			if (tilde == false) {
				auxiliaryMat = diu.fourierConvolve(negativeIndex(L1), diu.matrixOperations(wMat[i], diu.fourierConvolve(L1, guess[i]), "multiply"));
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.fourierConvolve(negativeIndex(L2), diu.matrixOperations(wMat[i], diu.fourierConvolve(L2, guess[i]), "multiply")), "add");
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.fourierConvolve(negativeIndex(L3), diu.matrixOperations(wMat[i], diu.fourierConvolve(L3, guess[i]), "multiply")), "add");
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.fourierConvolve(negativeIndex(L4), diu.matrixOperations(wMat[i], diu.fourierConvolve(L4, guess[i]), "multiply")), "add");
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.fourierConvolve(negativeIndex(L5), diu.matrixOperations(wMat[i], diu.fourierConvolve(L5, guess[i]), "multiply")), "add");
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.fourierConvolve(negativeIndex(L6), diu.matrixOperations(wMat[i], diu.fourierConvolve(L6, guess[i]), "multiply")), "add");
			
				energyMeasure[i] = diu.fourierConvolve(negativeIndex(psfMat), imgMat[i]);
				energyMeasure[i] = diu.matrixOperations(energyMeasure[i], diu.fourierConvolve(negativeIndex(psfMat), diu.fourierConvolve(psfMat, guess[i])), "subtract");
				energyMeasure[i] = diu.matrixOperations(energyMeasure[i], diu.scaleMat(diu.matrixOperations(nPrime[i], guess[i], "multiply"), 100*smooth), "subtract");
				energyMeasure[i] = diu.matrixOperations(energyMeasure[i], diu.scaleMat(diu.matrixOperations(wMat[i], guess[i], "multiply"), smooth), "subtract");
				energyMeasure[i] = diu.matrixOperations(energyMeasure[i], diu.scaleMat(auxiliaryMat, smooth), "subtract");
			}
			else {
				auxiliaryMat = diu.fourierConvolve(negativeIndex(L1), diu.matrixOperations(wMatTilde[i], diu.fourierConvolve(L1, guessTilde[i]), "multiply"));
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.fourierConvolve(negativeIndex(L2), diu.matrixOperations(wMatTilde[i], diu.fourierConvolve(L2, guessTilde[i]), "multiply")), "add");
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.fourierConvolve(negativeIndex(L3), diu.matrixOperations(wMatTilde[i], diu.fourierConvolve(L3, guessTilde[i]), "multiply")), "add");
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.fourierConvolve(negativeIndex(L4), diu.matrixOperations(wMatTilde[i], diu.fourierConvolve(L4, guessTilde[i]), "multiply")), "add");
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.fourierConvolve(negativeIndex(L5), diu.matrixOperations(wMatTilde[i], diu.fourierConvolve(L5, guessTilde[i]), "multiply")), "add");
				auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.fourierConvolve(negativeIndex(L6), diu.matrixOperations(wMatTilde[i], diu.fourierConvolve(L6, guessTilde[i]), "multiply")), "add");
			
				energyMeasureTilde[i] = diu.fourierConvolve(negativeIndex(psfMat), imgMat[i]);
				energyMeasureTilde[i] = diu.matrixOperations(energyMeasureTilde[i], diu.fourierConvolve(negativeIndex(psfMat), diu.fourierConvolve(psfMat, guessTilde[i])), "subtract");
				energyMeasureTilde[i] = diu.matrixOperations(energyMeasureTilde[i], diu.scaleMat(diu.matrixOperations(nPrimeTilde[i], guessTilde[i], "multiply"), 100*smooth), "subtract");
				energyMeasureTilde[i] = diu.matrixOperations(energyMeasureTilde[i], diu.scaleMat(diu.matrixOperations(wMatTilde[i], guessTilde[i], "multiply"), smooth), "subtract");
				energyMeasureTilde[i] = diu.matrixOperations(energyMeasureTilde[i], diu.scaleMat(auxiliaryMat, smooth), "subtract");
			}
		}
		
		get_error(tilde);
	}
	
	// get D matrix
	public void get_dMat() {
		float[][][] auxiliaryMat;
		for (int i = 0; i < frames; i++) {
			auxiliaryMat = diu.fourierConvolve(diu.matrixOperations(negativeIndex(L1), negativeIndex(L1), "multiply"), wMat[i]);
			auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.fourierConvolve(diu.matrixOperations(negativeIndex(L2), negativeIndex(L2), "multiply"), wMat[i]), "add");
			auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.fourierConvolve(diu.matrixOperations(negativeIndex(L3), negativeIndex(L3), "multiply"), wMat[i]), "add");
			auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.fourierConvolve(diu.matrixOperations(negativeIndex(L4), negativeIndex(L4), "multiply"), wMat[i]), "add");
			auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.fourierConvolve(diu.matrixOperations(negativeIndex(L5), negativeIndex(L5), "multiply"), wMat[i]), "add");
			auxiliaryMat = diu.matrixOperations(auxiliaryMat, diu.fourierConvolve(diu.matrixOperations(negativeIndex(L6), negativeIndex(L6), "multiply"), wMat[i]), "add");
			
			dMat[i] = diu.scaleMat(nPrime[i], 100*smooth);
			dMat[i] = diu.matrixOperations(dMat[i], diu.scaleMat(wMat[i], smooth), "add");
			dMat[i] = diu.matrixOperations(dMat[i], diu.scaleMat(auxiliaryMat, smooth), "add");
			dMat[i] = diu.incrementComplex(dMat[i], H0);
		}
	}
	
	// get U matrix
	public void get_uMat() {
		for (int i = 0; i < frames; i++) {
			uMat[i] = diu.matrixOperations(identityMat, dMat[i], "divide");
			uMat[i] = diu.matrixOperations(uMat[i], diu.fourierConvolve(piMatFT, energyMeasure[i]), "multiply");
			uMat[i] = diu.fourierConvolve(piMatFT, uMat[i]);
		}
	}
	
	// get guess(~)
	public void get_guessTilde() {
		for (int i = 0; i < frames; i++) {
			guessTilde[i] = diu.matrixOperations(guess[i], diu.scaleMat(uMat[i], damping), "add");
		}
	}
	
	// check if guess(~) is better than guess
	public boolean checkTilde() {
		if (errorTilde < error)
			return true;
		else
			return false;				
	}
	
	// update guess accordingly when guess(~) is better
	public void update() {
		for (int i = 0; i < frames; i++)
			for (int j = 0; j < slices; j++)
				for (int k = 0; k < height; k++)
					for (int l = 0; l < width; l++) {
						guess[i][j][k][2*l] = guessTilde[i][j][k][2*l];
						guess[i][j][k][2*l + 1] = guessTilde[i][j][k][2*l + 1];
						
						wMat[i][j][k][2*l] = wMatTilde[i][j][k][2*l];
						wMat[i][j][k][2*l + 1] = wMatTilde[i][j][k][2*l + 1];
						
						energyMeasure[i][j][k][2*l] = energyMeasureTilde[i][j][k][2*l];
						energyMeasure[i][j][k][2*l + 1] = energyMeasureTilde[i][j][k][2*l + 1];
						
						error = errorTilde;
					}
	}
	
	// turn complex matrices back into real matrices
	public void getAmplitude(float norm) {
		for (int i = 0; i < frames; i++) {
			guess[i] = diu.getAmplitudeMat(guess[i]);
			guess[i] = diu.formatWienerAmp(guess[i]);
			diu.linearShift(guess[i], 0, norm);
		}	
	}
	
	// take square root of complex number, return as {re, im}
	private float[] sqrtComplex(float re, float im) {
		float a = re;
		float b = im;
		float theta = (float) Math.atan2(b, a);
		
		float[] ret = new float[2];
		ret[0] = (float) (Math.pow(a*a + b*b, 0.25) * Math.cos(theta / 2));
		ret[1] = (float) (Math.pow(a*a + b*b, 0.25) * Math.sin(theta / 2));
		return ret;
	}
	
	// returns a complex matrix with all axes mirrored
	private float[][][] negativeIndex(float[][][] mat) {
		float[][][] retMat = new float[slices][height][2*width];
		for (int i = 0; i < slices; i++)
			for (int j = 0; j < height; j++)
				for (int k = 0; k < width; k++) {
					retMat[i][j][2*k] = mat[slices - i - 1][height - j - 1][2*width - 2*k - 2];
					retMat[i][j][2*k + 1] = mat[slices - i - 1][height - j - 1][2*width - 2*k - 1];
				}
		return retMat;
	}
}