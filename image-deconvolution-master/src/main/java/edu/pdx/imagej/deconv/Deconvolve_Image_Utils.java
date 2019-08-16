package edu.pdx.imagej.deconv;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.io.OpenDialog;
import ij.process.ImageProcessor;
import org.jtransforms.fft.FloatFFT_3D;
import java.math.BigDecimal;
import java.math.RoundingMode;

public class Deconvolve_Image_Utils {
	
	// returns (a + b*i) = (c + d*i)/(e + f*i) as {a, b}
	private float[] complexDivide(float c, float d, float e, float f) {
		double denom = Math.pow(e, 2) + Math.pow(f, 2);
		float re = (float)((c*e + d*f) / denom);
		float im = (float)((d*e - c*f) / denom);
		
		if (!Float.isFinite(re) || !Float.isFinite(im))
			return complexDivideBig(c, d, e, f);
		
		float[] ret = {re, im};
		return ret;
	}
	
	// handle complex division with big numbers
	private float[] complexDivideBig(float c, float d, float e, float f) {
		try {
			BigDecimal bigC = BigDecimal.valueOf((double)c);
			BigDecimal bigD = BigDecimal.valueOf((double)d);
			BigDecimal bigE = BigDecimal.valueOf((double)e);
			BigDecimal bigF = BigDecimal.valueOf((double)f);
		
			BigDecimal denom = bigE.multiply(bigE).add(bigF.multiply(bigF));
			BigDecimal re = bigC.multiply(bigE).add(bigD.multiply(bigF)).divide(denom, RoundingMode.HALF_UP);
			BigDecimal im = bigD.multiply(bigE).subtract(bigC.multiply(bigF)).divide(denom, RoundingMode.HALF_UP);
		
			float[] ret = {re.floatValue(), im.floatValue()};
			return ret;
		}
		catch (NumberFormatException ex) {
			float[] ret = {0, 0};
			return ret;
		}
	}
	
	// returns (a + b*i) = (c + d*i)*(e + f*i) as {a, b}
	private float[] complexMult(float c, float d, float e, float f) {
		float re = (c*e - d*f);
		float im = (c*f + d*e);
		
		if (!Float.isFinite(re) || !Float.isFinite(im))
			return complexMultBig(c, d, e, f);
		
		float[] ret = {re, im};
		return ret;
	}
	
	// handle complex multiplication with big numbers
	private float[] complexMultBig(float c, float d, float e, float f) {
		try {
			BigDecimal bigC = BigDecimal.valueOf((double)c);
			BigDecimal bigD = BigDecimal.valueOf((double)d);
			BigDecimal bigE = BigDecimal.valueOf((double)e);
			BigDecimal bigF = BigDecimal.valueOf((double)f);
		
			BigDecimal re = bigC.multiply(bigE).subtract(bigD.multiply(bigF));
			BigDecimal im = bigC.multiply(bigF).subtract(bigD.multiply(bigE));
		
			float[] ret = {re.floatValue(), im.floatValue()};
			return ret;
		}
		catch(NumberFormatException ex) {
			float[] ret = {0, 0};
			return ret;
		}
	}
	
	// returns (a + b*i) = (c + d*i) - (e + f*i) as {a, b}
	private float[] complexSub(float c, float d, float e, float f) {
		float re = c - e;
		float im = d - f;
		float[] ret = {re, im};
		
		if (!Float.isFinite(re) || !Float.isFinite(im))
			return complexSubBig(c, d, e, f);
		
		return ret;
	}
	
	// handle complex subtraction with big numbers
	private float[] complexSubBig(float c, float d, float e, float f) {
		try {
			BigDecimal bigC = BigDecimal.valueOf((double)c);
			BigDecimal bigD = BigDecimal.valueOf((double)d);
			BigDecimal bigE = BigDecimal.valueOf((double)e);
			BigDecimal bigF = BigDecimal.valueOf((double)f);
		
			BigDecimal re = bigC.subtract(bigE);
			BigDecimal im = bigD.subtract(bigF);
		
			float[] ret = {re.floatValue(), im.floatValue()};
			return ret;
		}
		catch(NumberFormatException ex) {
			float[] ret = {0, 0};
			return ret;
		}
	}
	
	// returns (a + b*i) = (c + d*i) + (e + f*i) as {a, b}
	private float[] complexAdd(float c, float d, float e, float f) {
		float re = c + e;
		float im = d + f;
		float[] ret = {re, im};
		
		if (!Float.isFinite(re) || !Float.isFinite(im))
			return complexAddBig(c, d, e, f);
		
		return ret;
	}
	
	// handle complex addition with big numbers
	private float[] complexAddBig(float c, float d, float e, float f) {
		try {
			BigDecimal bigC = BigDecimal.valueOf((double)c);
			BigDecimal bigD = BigDecimal.valueOf((double)d);
			BigDecimal bigE = BigDecimal.valueOf((double)e);
			BigDecimal bigF = BigDecimal.valueOf((double)f);
		
			BigDecimal re = bigC.add(bigE);
			BigDecimal im = bigD.add(bigF);
		
			float[] ret = {re.floatValue(), im.floatValue()};
			return ret;
		}
		catch(NumberFormatException ex) {
			float[] ret = {0, 0};
			return ret;
		}
	}
	
	// take a reference to a 32-bit image slice's pixel array and assign a new image slice from a 3D matrix
	private void assignPixels(float[] pixels, float[][][] newMat, int slice) {
		int height = newMat[0][0].length;
		int width = newMat[0].length;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				pixels[x + y*width] = (float)newMat[slice][x][y];
			}
		}	
	}
	
	// take a reference to an 8-bit image slice's pixel array and assign a new image slice from a 3D matrix
	private void assignPixels(byte[] pixels, float[][][] newMat, int slice) {
		int height = newMat[0][0].length;
		int width = newMat[0].length;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y*width] = (byte)newMat[slice][x][y];
			}
		}	
	}
	
	// take a reference to a 16-bit image slice's pixel array and assign a new image slice from a 3D matrix
	private void assignPixels(short[] pixels, float[][][] newMat, int slice) {
		int height = newMat[0][0].length;
		int width = newMat[0].length;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				// process each pixel of the line
				// example: add 'number' to each pixel
				pixels[x + y*width] = (short)newMat[slice][x][y];
			}
		}	
	}
	
	// Show dialog window with a given message. Returns true if user presses "OK", false if user closes window or presses "Cancel"
	private boolean showMessage(String message) {
		GenericDialog gd = new GenericDialog("Information");
		
		gd.addMessage(message);
		gd.showDialog();
		
		if (gd.wasCanceled())
			return false;
		
		return true;
	}
	
	// Show window to select an image file. Returns the file path as a string.
	public String getPath(String message) {
		String path;
		OpenDialog od = new OpenDialog(message);
		path = od.getPath();
		while (path == null) {
			if(showMessage("Please select a file.")) {
				od = new OpenDialog(message);
				path = od.getPath();
			}
			else
				break;
		}
		return path;
	}
	
	// Takes an image stack and returns a float 3D matrix
	public float[][][] getMatrix3D(ImagePlus image) {
		float[][][] mat = getMatrix4D(image)[0];
		return mat;
	}
	
	// Takes an image stack and returns a float 4D matrix
	public float[][][][] getMatrix4D(ImagePlus image) {
		int width = image.getWidth();
		int height = image.getHeight();
		int slices = image.getNSlices();
		int frames = image.getNFrames();
		ImageStack stack = image.getStack();
		float[][][][] mat = new float[frames][slices][height][width];
		
		// loop over image stack and assign values to matrix - note frames, slices, and StackIndex are 1-based
		for (int i = 1; i <= frames; i++ ) {
			for (int j = 1; j <= slices; j++) {
				for (int k = 0; k < height; k++) {
					for (int l = 0; l < width; l++)
						mat[i-1][j-1][k][l] = (float)stack.getVoxel(l, k, image.getStackIndex(1, j, i)-1);
				}
			}
		}
		return mat;
	}
	
	// Takes 3D matrix and puts it into a form compatible with the FFT package
	// Even columns are the real parts of data entries, and odd columns are the imaginary parts.	
	// This method assumes a phase of zero, so all the data is real.
	public float[][][] toFFTform(float[][][] mat) {
		int a_slices = mat.length;
		int a_height = mat[0].length;
		int a_width = mat[0][0].length;
		float[][][] ret = new float[a_slices][a_height][2 * a_width];

		for (int i = 0; i < a_slices; i++) {
			for (int j = 0; j < a_height; j++) {
				for (int k = 0; k < a_width; k++) {
					// set the real part
					ret[i][j][2 * k] = mat[i][j][k];
					ret[i][j][2 * k + 1] = 0;	
				}
			}
		}
		return ret;
	}
	
	// takes a "complex" matrix and squish it back to a simple amplitude matrix
	public float[][][] getAmplitudeMat(float[][][] mat) {
		int slices = mat.length;
		int height = mat[0].length;
		// the complex matrix has twice the width as the output matrix
		int width = (int)(mat[0][0].length / 2);
		float[][][] ret = new float[slices][height][width];
		for (int i = 0; i < slices; i++)
			for (int j = 0; j < height; j++)
				for (int k = 0; k < width; k++)
					ret[i][j][k] = (float)Math.sqrt((double)mat[i][j][2*k] * (double)mat[i][j][2*k] + (double)mat[i][j][2*k + 1] * (double)mat[i][j][2*k + 1]); 
		
		return ret;
	}
	
	// adds inc to each real element of a complex 3D matrix
	public float[][][] incrementComplex(float[][][] mat, float inc) {
		int slices = mat.length;
		int height = mat[0].length;
		int width = (int)(mat[0][0].length / 2);
		float[][][] ret = new float[slices][height][2*width];
		for (int i = 0; i < slices; i++)
			for (int j = 0; j < height; j++)
				for (int k = 0; k < width; k++) {
					ret[i][j][2*k] = mat[i][j][2*k] + inc; 
					ret[i][j][2*k + 1] = mat[i][j][2*k + 1];
				}
		
		return ret;
	}
	
	// divides, multiplies, subtracts, or adds corresponding elements in two 3D matrices
	// these are complex matrices, so additional operations are required
	public float[][][] matrixOperations(float[][][] mat1, float[][][] mat2, String operation) {
		int slices = mat1.length;
		int height = mat1[0].length;
		int width = (int)(mat1[0][0].length / 2);
		float[][][] retMat = new float[slices][height][2*width];
		
		float c; //Re of mat1 element
		float d; //Im of mat1 element
		float e; //Re of mat2 element
		float f; //Im of mat2 element
		float[] result;
		
		for (int i = 0; i < slices; i++)
			for (int j = 0; j < height; j++)
				for (int k = 0; k < width; k++) {
					c = mat1[i][j][2*k];
					d = mat1[i][j][2*k+1];
					e = mat2[i][j][2*k];
					f = mat2[i][j][2*k + 1];
					if (operation == "divide") {
						result = complexDivide(c, d, e, f);
						// real part of dividing two complex numbers
						retMat[i][j][2*k] = result[0];
						// imaginary part of dividing two complex numbers
						retMat[i][j][2*k + 1] = result[1];
					}
					else if (operation == "multiply") {
						result = complexMult(c, d, e, f);
						// real part of multiplying two complex numbers
						retMat[i][j][2*k] = result[0];
						// imaginary part of multiplying two complex numbers
						retMat[i][j][2*k + 1] = result[1];
					}
					else if (operation == "subtract") {
						result = complexSub(c, d, e, f);
						retMat[i][j][2*k] = result[0];
						retMat[i][j][2*k + 1] = result[1];
					}
					else {
						result = complexAdd(c, d, e, f);
						retMat[i][j][2*k] = result[0];
						retMat[i][j][2*k + 1] = result[1];
					}
				}
		return retMat;
	}
	
	// returns the complex conjugate of the input matrix
	public float[][][] complexConj(float[][][] mat) {
		int slices = mat.length;
		int height = mat[0].length;
		int width = (int)(mat[0][0].length / 2);
		float[][][] retMat = new float[slices][height][2*width];
		
		for (int i = 0; i < slices; i++)
			for (int j = 0; j < height; j++)
				for (int k = 0; k < width; k++) {
					retMat[i][j][2*k] = mat[i][j][2*k];
					retMat[i][j][2*k + 1] = (-1)*mat[i][j][2*k + 1];
				}
		
		return retMat;
	}
	
	// scales a  matrix (can be real or complex)
	public float[][][] scaleMat(float[][][] mat, float scale) {
		int slices = mat.length;
		int height = mat[0].length;
		int width = mat[0][0].length;
		float[][][] retMat = new float[slices][height][width];
		
		for (int i = 0; i < slices; i++)
			for (int j = 0; j < height; j++)
				for (int k = 0; k < width; k++) {
					retMat[i][j][k] = scale * mat[i][j][k];
				}
		
		return retMat;
	}
	
	// covert 3D matrix to ImagePlus image
	public ImagePlus reassign(float[][][] testMat, String impType, String title) {
		int slices = testMat.length;
		int height = testMat[0][0].length;
		int width = testMat[0].length;
		
		ImagePlus testImage = IJ.createImage(title, impType, width, height, slices);
		for (int i = 0; i < slices; i++) {
			if (impType == "GRAY32")
				assignPixels((float[])testImage.getStack().getProcessor(i+1).getPixels(), testMat, i);
			else if (impType == "GRAY16")
				assignPixels((short[])testImage.getStack().getProcessor(i+1).getPixels(), testMat, i);
			else
				assignPixels((byte[])testImage.getStack().getProcessor(i+1).getPixels(), testMat, i);
			IJ.showProgress(i, slices);
		}
		
		return testImage;
	}
	
	// covert 4D matrix to ImagePlus image
	public ImagePlus reassign(float[][][][] testMat, String impType, String title) {
		int frames = testMat.length;
		int width = testMat[0][0][0].length;
		int height = testMat[0][0].length;
		int slices = testMat[0].length;
		int bitdepth = 24;
		
		if (impType == "GRAY32")
			bitdepth = 32;
		if (impType == "GRAY16")
			bitdepth = 16;
		if (impType == "GRAY8")
			bitdepth = 8;
		
		ImagePlus testImage = IJ.createHyperStack("Result", width, height, 1, slices, frames, bitdepth);
		ImageStack stack = testImage.getStack();
		
		for (int i = 1; i <= frames; i++) 
			for (int j = 1; j <= slices; j++)
				for (int k = 0; k < height; k++)
					for (int l = 0; l < width; l++)
						stack.setVoxel(l, k, testImage.getStackIndex(1, j, i)-1, (double)testMat[i-1][j-1][k][l]);

		return testImage;
	}
	
	// normalize a 3D matrix so that all values fall between newMin and newMax
	public void normalize (float[][][] mat, float newMin, float newMax) {
		float min = mat[0][0][0];
		float max = mat[0][0][0];
		for (int i = 0; i < mat.length; i++)
			for (int j = 0; j < mat[0].length; j++) 
				for (int k = 0; k < mat[0][0].length; k++) {
					if (mat[i][j][k] > max)
						max = mat[i][j][k];
					if (mat[i][j][k] < min)
						min = mat[i][j][k];
				}
		
		for (int i = 0; i < mat.length; i++)
			for (int j = 0; j < mat[0].length; j++) 
				for (int k = 0; k < mat[0][0].length; k++)
					mat[i][j][k] = (mat[i][j][k] - min)*(newMax - newMin)/(max - min) + newMin;
	}
	
	// invert an image
    public void invert(ImagePlus imp) {
    	ImageProcessor ip;
		for (int i = 1; i <= imp.getStackSize(); i++) {
			ip = imp.getStack().getProcessor(i);
			ip.invert();
		}
      }
    
    // normalize a real PSF matrix so that all pixels add to 1
    public void normalizePSF(float[][][] psfMat) {
    	float total = 0;
    	for (int i = 0; i < psfMat.length; i++)
    		for (int j = 0; j < psfMat[0].length; j++)
    			for (int k = 0; k < psfMat[0][0].length; k++)
    				total += psfMat[i][j][k];
    	
    	for (int i = 0; i < psfMat.length; i++)
    		for (int j = 0; j < psfMat[0].length; j++)
    			for (int k = 0; k < psfMat[0][0].length; k++)
    				psfMat[i][j][k] = psfMat[i][j][k] / total;
    }
    
    // after deconvolution, the quadrants of the image are flipped around for some reason. This puts it back to normal.
    public float[][][] formatWienerAmp(float[][][] ampMat) {
    	int slices = ampMat.length;
    	int height = ampMat[0].length;
    	int width = ampMat[0][0].length;
    	float placehold;
    	int halfSlices = (int)(slices / 2);
    	int halfHeight = (int)(height / 2);
    	int halfWidth = (int)(width / 2);
    	float[][][] reformat = new float[slices][height][width];
    	for (int i = 0; i < slices; i++) {
    		if (i + halfSlices < slices)
    			reformat[i + halfSlices] = ampMat[i];
    		else
    			if (slices % 2 == 0)
    				reformat[i - halfSlices] = ampMat[i];
    			else
    				reformat[i - halfSlices - 1] = ampMat[i];
    		}
    	
    	for (int i = 0; i < slices; i++) {
    		for (int j = 0; j < height/2; j++)
    			for (int k = 0; k < width/2; k++) {
    				placehold = reformat[i][j][k];
    				reformat[i][j][k] = reformat[i][j+halfHeight-1][k+halfWidth-1];
    				reformat[i][j+halfHeight-1][k+halfWidth-1] = placehold;
    			}
    		
    		for (int j = 0; j < height / 2; j++)
    			for (int k = width/2; k < width; k++) {
    				placehold = reformat[i][j][k];
    				reformat[i][j][k] = reformat[i][j+halfHeight-1][k-halfWidth+1];
    				reformat[i][j+halfHeight-1][k-halfWidth+1] = placehold;
    			}
    	}
   
 
    	return reformat;
    }
    
    // convolve two matrices by elementwise multiplication in Fourier space. mat1, mat2, and ret are all in FFT form
	public float[][][] fourierConvolve(float[][][] mat1, float[][][] mat2) {
		FloatFFT_3D fft = new FloatFFT_3D((long)mat1.length, (long)mat1[0].length, (long)mat1[0][0].length/2);
		float[][][] mat1FT = new float[mat1.length][mat1[0].length][mat1[0][0].length];
		float[][][] mat2FT = new float[mat1.length][mat1[0].length][mat1[0][0].length];
		float[][][] retMat = new float[mat1.length][mat1[0].length][mat1[0][0].length];
		
		// make copy of mat1 and mat2 so we don't change them
		for (int i = 0; i < mat1.length; i++)
			for (int j = 0; j < mat1[0].length; j++) 
				for (int k = 0; k < mat1[0][0].length; k++) {
					mat1FT[i][j][k] = mat1[i][j][k];
					mat2FT[i][j][k] = mat2[i][j][k];
				}		
	
		fft.complexForward(mat1FT);
		fft.complexForward(mat2FT);
		
		retMat = matrixOperations(mat1FT, mat2FT, "multiply");
		fft.complexInverse(retMat, true);
		
		return retMat;
	}
}