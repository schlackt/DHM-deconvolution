/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package edu.pdx.imagej.deconv;

import java.io.File;

import ij.IJ;
import ij.ImagePlus;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

public class Deconvolve_Iterative implements PlugInFilter {
	protected ImagePlus image;
	protected ImagePlus PSF;
	protected ImagePlus phaseImage;

	private int width;
	private int height;
	private int slices;
	private int frames;
	private int iterations;
	private String path;
	private String choice;
	private String divisor;
	private String decon_choice;
	private String stack_path;
	private String stack_path_phase;
	private String save_path;
	private File stacks;
	private Calibration cal;
	private String[] stack_list;
	private String[] stack_list_phase;
	private boolean getSNR;
	private boolean normalizePSF;
	private boolean decon_hyper;
	private boolean save_files;
	private float SNR;
	private float[][][][] ampMat;
	private float[][][][] phaseMat;
	private float[][][][] imgMat;
	private float[][][][] imgMatPhase;
	private float[][][][] objMat;
	private float[][][] psfMat;
	private float[][][] psfPhaseMat;
	
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
		String[] decon_choices = {"Standard", "Complex (Polar)", "Complex (Rectangular)"};
		GenericDialog gd = new GenericDialog("Deconvolution Setup");
		gd.addChoice("Output Image:", choices, "32-bit");
		gd.addChoice("Deconvolution Style: ", decon_choices, "Standard");
		gd.addNumericField("Iterations:", 5, 0);
		gd.addCheckbox("Get SNR?", false);
		gd.addCheckbox("Normalize PSF?", true);
		gd.addCheckbox("Deconvolve from Hyperstack?", true);
		gd.addCheckbox("Save by Frame?", false);

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		// get entered values
		choice = gd.getNextChoice();
		decon_choice = gd.getNextChoice();
		iterations = (int) gd.getNextNumber();
		getSNR = gd.getNextBoolean();
		normalizePSF = gd.getNextBoolean();
		decon_hyper = gd.getNextBoolean();
		save_files = gd.getNextBoolean();
		
		if (!getSNR) {
			GenericDialog gd2 = new GenericDialog("Custom Beta");
			gd2.addNumericField("Beta:", 0.001, 3);
			
			gd2.showDialog();
			if (gd2.wasCanceled())
				return false;
			
			SNR = (float) (1 / gd2.getNextNumber());
		}
		
		// find the stack directory and get a list of the files in it
		if (!decon_hyper && decon_choice == "Standard") {
			stack_path = diu.getDirectory("Please select the folder of stacks:");
			stacks = new File(stack_path);
			stack_list = stacks.list();
		}
		
		if (!decon_hyper && decon_choice == "Complex (Polar)") {
			stack_path = diu.getDirectory("Please select the folder of amplitude stacks:");
			stacks = new File(stack_path);
			stack_list = stacks.list();
			
			stack_path_phase = diu.getDirectory("Please select the folder of phase stacks:");
			stacks = new File(stack_path);
			stack_list_phase = stacks.list();
		}
		
		if (!decon_hyper && decon_choice == "Complex (Rectangular)") {
			stack_path = diu.getDirectory("Please select the folder of real stacks:");
			stacks = new File(stack_path);
			stack_list = stacks.list();
			
			stack_path_phase = diu.getDirectory("Please select the folder of imaginary stacks:");
			stacks = new File(stack_path);
			stack_list_phase = stacks.list();
		}
		
		// get desired save directory
		if (save_files) {
			save_path = diu.getDirectory("Select the save directory:");
			save_path += "Deconvolved";
			new File(save_path).mkdirs();

			if (save_path.indexOf('\\') >= 0) 
				divisor = "\\";
			else
				divisor= "/";
			
			save_path += divisor;
			
			if (decon_choice == "Complex (Polar)") {
				new File(save_path + "Amplitude").mkdirs();
				new File(save_path + "Phase").mkdirs();
			}
			
			if (decon_choice == "Complex (Rectangular)") {
				new File(save_path + "Real").mkdirs();
				new File(save_path + "Imaginary").mkdirs();
			}	
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
		path = diu.getPath("Select the PSF real or amplitude image:");
		if (path == null) {
			return;
		}
		PSF = IJ.openImage(path);
		width = PSF.getProcessor().getWidth();
		height = PSF.getProcessor().getHeight();
		slices = PSF.getNSlices();
		
		if (getSNR) {
			// get signal-to-noise through user input
			Noise_NP nnp = new Noise_NP();
			float noiseDev = nnp.getNoise(image);
			float signal = nnp.getSignal(image);
			SNR = signal / noiseDev;
		}
		
		IJ.showStatus("Preprocessing...");
		
		// convert image stacks to matrices
		psfMat = diu.getMatrix3D(PSF);
		cal = PSF.getCalibration();
		PSF.close();
		
		if (decon_choice != "Standard") {
			path = diu.getPath("Select the PSF imaginary or phase image:");
			PSF = IJ.openImage(path);
			psfPhaseMat = diu.getMatrix3D(PSF);
		}
		
		if (normalizePSF && decon_choice == "Complex (Polar)")
			diu.normalize(psfMat);
		
		if (normalizePSF && decon_choice == "Complex (Rectangular)")
			diu.normalize(psfMat, psfPhaseMat);
			
		if (decon_hyper) {
			if (decon_choice != "Standard") {
				path = diu.getPath("Select the imaginary or phase image:");
				ImagePlus temp = IJ.openImage(path);
				phaseMat = diu.getMatrix4D(temp);
			}
			
			if (save_files)
				save_from_hyperstack();
			else
				show_from_hyperstack();
		}
		else {
			if (save_files)
				save_from_files();
			else
				show_from_files();
		}
		
	}
	
	public void save_from_hyperstack() {
		ampMat = diu.getMatrix4D(image);
		IJ.showStatus("Deconvolving hyperstack...");
		
		if (decon_choice == "Standard")
			deconvolve(diu.toFFTform(ampMat), diu.toFFTform(psfMat));
		
		else if (decon_choice == "Complex (Polar)") 	
			deconvolve(diu.toFFTform(ampMat, phaseMat), diu.toFFTform(psfMat, psfPhaseMat));
		
		else		
			deconvolve(diu.toFFTformRect(ampMat, phaseMat), diu.toFFTform(psfMat, psfPhaseMat));
		
		IJ.showStatus("Saving images...");
		for (int i = 0; i < frames; i++) {
			ImagePlus tempImg = diu.reassign(diu.getAmplitudeMat(imgMat[i]), choice, Integer.toString(i));
			tempImg.setCalibration(cal);
			
			if (decon_choice == "Standard")
				IJ.saveAsTiff(tempImg, save_path + Integer.toString(i) + ".tif");
			else if (decon_choice == "Complex (Polar)") {
				IJ.saveAsTiff(tempImg, save_path + "Amplitude" + divisor + Integer.toString(i) + ".tif");
				
				tempImg = diu.reassign(diu.getPhaseMat(imgMat[i]), choice, Integer.toString(i));
				tempImg.setCalibration(cal);
				IJ.saveAsTiff(tempImg, save_path + "Phase" + divisor + Integer.toString(i) + ".tif");
			}
			else {
				tempImg = diu.reassign(diu.getReMat(imgMat[i]), choice, Integer.toString(i));
				tempImg.setCalibration(cal);
				IJ.saveAsTiff(tempImg, save_path + "Real" + divisor + Integer.toString(i) + ".tif");
				
				tempImg = diu.reassign(diu.getImMat(imgMat[i]), choice, Integer.toString(i));
				tempImg.setCalibration(cal);
				IJ.saveAsTiff(tempImg, save_path + "Imaginary" + divisor + Integer.toString(i) + ".tif");
			}				
		}
	}
	
	public void save_from_files() {
		ampMat = new float[1][slices][height][width];
		if (decon_choice != "Standard")
			phaseMat = new float[1][slices][height][width];
		
		for (int i = 0; i < stack_list.length; i++) {
			IJ.showStatus("Processing frame " + Integer.toString(i + 1) + " of " + Integer.toString(stack_list.length) + "...");
			ImagePlus tempImg = IJ.openImage(stack_path + stack_list[i]);
			ampMat = diu.getMatrix4D(tempImg);
			tempImg.close();
			
			if (decon_choice == "Standard") {
				ampMat = diu.toFFTform(ampMat);
				psfMat = diu.toFFTform(psfMat);
				deconvolve(ampMat, psfMat);
				
				tempImg = diu.reassign(diu.getAmplitudeMat(imgMat), choice, Integer.toString(i));
				tempImg.setCalibration(cal);
				IJ.saveAsTiff(tempImg, save_path + Integer.toString(i) + ".tif");
				tempImg.close();
			}
			else if (decon_choice == "Complex (Polar)") {
				ImagePlus phaseImg = IJ.openImage(stack_path_phase + stack_list_phase[i]);
				phaseMat = diu.getMatrix4D(phaseImg);
				phaseImg.close();
				
				deconvolve(diu.toFFTform(ampMat, phaseMat), diu.toFFTform(psfMat, psfPhaseMat));
				
				tempImg = diu.reassign(diu.getAmplitudeMat(imgMat), choice, Integer.toString(i));
				tempImg.setCalibration(cal);
				IJ.saveAsTiff(tempImg, save_path + "Amplitude" + divisor + Integer.toString(i) + ".tif");
				
				tempImg = diu.reassign(diu.getPhaseMat(imgMat), choice, Integer.toString(i));
				tempImg.setCalibration(cal);
				IJ.saveAsTiff(tempImg, save_path + "Phase" + divisor + Integer.toString(i) + ".tif");
				tempImg.close();
			}
			else {
				ImagePlus imImg = IJ.openImage(stack_path_phase + stack_list_phase[i]);
				phaseMat = diu.getMatrix4D(imImg);
				imImg.close();
				
				deconvolve(diu.toFFTformRect(ampMat, phaseMat), diu.toFFTformRect(psfMat, psfPhaseMat));
				
				tempImg = diu.reassign(diu.getReMat(imgMat), choice, Integer.toString(i));
				tempImg.setCalibration(cal);
				IJ.saveAsTiff(tempImg, save_path + "Real" + divisor + Integer.toString(i) + ".tif");
				
				tempImg = diu.reassign(diu.getImMat(imgMat), choice, Integer.toString(i));
				tempImg.setCalibration(cal);
				IJ.saveAsTiff(tempImg, save_path + "Imaginary" + divisor + Integer.toString(i) + ".tif");
				tempImg.close();
			}
		
				
		}
	}
	
	public void show_from_hyperstack() {
		ampMat = diu.getMatrix4D(image);
		IJ.showStatus("Deconvolving hyperstack...");
		
		if (decon_choice == "Standard") {
			deconvolve(diu.toFFTform(ampMat), diu.toFFTform(psfMat));
			
			IJ.showStatus("Constructing result...");
			ImagePlus tempImage = diu.reassign(diu.getAmplitudeMat(imgMat), choice, "Result");
			tempImage.setCalibration(cal);
		
			tempImage.show();
		}
		
		else if (decon_choice == "Complex (Polar)") {
			deconvolve(diu.toFFTform(ampMat, phaseMat), diu.toFFTform(psfMat, psfPhaseMat));
			
			IJ.showStatus("Constructing result...");
			ImagePlus ampImage = diu.reassign(diu.getAmplitudeMat(imgMat), choice, "Amplitude");
			ampImage.setCalibration(cal);
			ampImage.show();
			
			ImagePlus phaseImage = diu.reassign(diu.getPhaseMat(imgMat), choice, "Phase");
			phaseImage.setCalibration(cal);
			phaseImage.show();
		}
		
		else {
			deconvolve(diu.toFFTform(ampMat, phaseMat), diu.toFFTform(psfMat, psfPhaseMat));
			
			IJ.showStatus("Constructing result...");
			ImagePlus reImage = diu.reassign(diu.getReMat(imgMat), choice, "Real");
			reImage.setCalibration(cal);
			reImage.show();
			
			ImagePlus imImage = diu.reassign(diu.getImMat(imgMat), choice, "Imaginary");
			imImage.setCalibration(cal);
			imImage.show();
		}	
	}
	
	public void show_from_files() {
		objMat = new float[stack_list.length][slices][height][width];
		ampMat = new float[1][slices][height][width];
		
		if (decon_choice != "Standard") {
			imgMatPhase = new float[stack_list.length][slices][height][width];
			phaseMat = new float[1][slices][height][width];
		}
		
		for (int i = 0; i < stack_list.length; i++) {
			IJ.showStatus("Processing frame " + Integer.toString(i + 1) + " of " + Integer.toString(stack_list.length) + "...");
			ImagePlus tempImg = IJ.openImage(stack_path + stack_list[i]);
			ampMat = diu.getMatrix4D(tempImg);
			tempImg.close();
			
			if (decon_choice == "Standard") {
				deconvolve(diu.toFFTform(ampMat), diu.toFFTform(psfMat));
				objMat[i] = diu.getAmplitudeMat(imgMat)[0];
				tempImg.close();
			}
			else if (decon_choice == "Complex (Polar)") {
				ImagePlus phaseImg = IJ.openImage(stack_path_phase + stack_list_phase[i]);
				phaseMat = diu.getMatrix4D(phaseImg);
				phaseImg.close();
				
				deconvolve(diu.toFFTform(ampMat, phaseMat), diu.toFFTform(psfMat, psfPhaseMat));
				objMat[i] = diu.getAmplitudeMat(imgMat)[0];
				imgMatPhase[i] = diu.getPhaseMat(imgMat)[0];
			}
			else {
				ImagePlus imImg = IJ.openImage(stack_path_phase + stack_list_phase[i]);
				phaseMat = diu.getMatrix4D(imImg);
				imImg.close();
				
				deconvolve(diu.toFFTformRect(ampMat, phaseMat), diu.toFFTformRect(psfMat, psfPhaseMat));
				objMat[i] = diu.getReMat(imgMat)[0];
				imgMatPhase[i] = diu.getImMat(imgMat)[0];
			}
		
				
		}
		
		if (decon_choice == "Standard") {
			ImagePlus tempImage = diu.reassign(objMat, choice, "Result");
			tempImage.setCalibration(cal);

			tempImage.show();
		}
		else if (decon_choice == "Complex (Polar)") {
			ImagePlus amp = diu.reassign(objMat, choice, "Amplitude");
			amp.setCalibration(cal);
			amp.show();
			
			ImagePlus phase = diu.reassign(imgMatPhase, choice, "Phase");
			phase.setCalibration(cal);
			phase.show();
		}
		else {
			ImagePlus reImage = diu.reassign(objMat, choice, "Real");
			reImage.setCalibration(cal);
			reImage.show();
			
			ImagePlus imImage = diu.reassign(imgMatPhase, choice, "Imaginary");
			imImage.setCalibration(cal);
			imImage.show();
		}
	}

	// standard iterative deconvolution. assumes image and psf are already in FFT form
	public void deconvolve(float[][][][] image, float[][][] psf) {
		imgMat = new float[image.length][image[0].length][image[0][0].length][image[0][0][0].length];
		float[][][][] blurredMat = new float[image.length][image[0].length][image[0][0].length][image[0][0][0].length];
		imgMat = diu.scaleMat(image, 1);
		for (int i = 0; i < iterations; i++)
			for (int j = 0; j < image.length; j++) {
				blurredMat[j] = diu.fourierConvolve(imgMat[j], psf);
				
				imgMat[j] = diu.matrixOperations(imgMat[j], image[j], "multiply");
				imgMat[j] = diu.matrixOperations(imgMat[j], diu.complexConj(blurredMat[j]), "multiply");
				imgMat[j] = diu.matrixOperations(imgMat[j], diu.incrementComplex(diu.matrixOperations(blurredMat[j], diu.complexConj(blurredMat[j]), "multiply"), 1/SNR), "divide");
			}
	}
	
	public void showAbout() {
		IJ.showMessage("DeconvolveImage",
			"Deconvolves DHM images using the Wiener filter."
		);
	}
}