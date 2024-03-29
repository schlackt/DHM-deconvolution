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
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.measure.Calibration;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

public class Hyper_Wiener_Filter implements PlugInFilter {
	protected ImagePlus image_amp;
	protected ImagePlus PSF_amp;
	protected ImagePlus image_phase;
	protected ImagePlus PSF_phase;

	private int width;
	private int height;
	private int slices;
	private int frames;
	private String choice;
	private String divisor;
	private String decon_choice;
	private String stack_path;
	private String stack_path_phase;
	private String save_path;
	private String amp_selection;
	private String phase_selection;
	private String PSF_amp_selection;
	private String PSF_phase_selection;
	private File stacks;
	private Calibration cal;
	private String[] stack_list;
	private String[] stack_list_phase;
	private boolean getSNR;
	private boolean normalizePSF;
	private boolean get_error;
	private boolean decon_hyper;
	private boolean save_files;
	private boolean intensity;
	private float SNR;
	private float[][][][] ampMat;
	private float[][][][] phaseMat;
	private float[][][][] imgMat;
	private float[][][][] imgMatPhase;
	private float[][][] psfMat;
	private float[][][] psfPhaseMat;
	
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
	
	// Show window for various settings
	private boolean showDialog() {
		String[] choices = {"8-bit", "16-bit", "32-bit"};
		String[] decon_choices = {"Standard", "Complex (Polar)", "Complex (Rectangular)"};
		String[] image_list = diu.imageList();
		GenericDialog gd = new GenericDialog("Deconvolution Setup");
		gd.addChoice("Output image:", choices, "32-bit");
		gd.addChoice("Deconvolution style: ", decon_choices, "Standard");
		gd.addChoice("Amplitude/Real image: ", image_list, image_list[image_list.length - 1]);
		gd.addChoice("Phase/Imaginary image: ", image_list, image_list[image_list.length - 1]);
		gd.addChoice("PSF amplitude/real image: ", image_list, image_list[image_list.length - 1]);
		gd.addChoice("PSF phase/imaginary image: ", image_list, image_list[image_list.length - 1]);
		gd.addCheckbox("Get SNR?", false);
		gd.addCheckbox("Normalize PSF?", true);
		gd.addCheckbox("Use intensity maps?", false);
		gd.addCheckbox("Display error?", false);
		gd.addCheckbox("Deconvolve from files?", false);
		gd.addCheckbox("Save by frame?", false);

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		// get entered values
		choice = gd.getNextChoice();
		decon_choice = gd.getNextChoice();
		amp_selection = gd.getNextChoice();
		phase_selection = gd.getNextChoice();
		PSF_amp_selection = gd.getNextChoice();
		PSF_phase_selection = gd.getNextChoice();
		getSNR = gd.getNextBoolean();
		normalizePSF = gd.getNextBoolean();
		intensity = gd.getNextBoolean();
		get_error = gd.getNextBoolean();
		decon_hyper = !gd.getNextBoolean();
		save_files = gd.getNextBoolean();	
		
		// ensure required images are entered
		if (amp_selection == "<none>" || PSF_amp_selection == "<none>") {
			IJ.showMessage("Amplitude/Real images are required for deconvolution.");
			return showDialog();
		}
		if (decon_choice != "Standard" && (phase_selection == "<none>" || PSF_phase_selection == "<none>")) {
			IJ.showMessage("Phase/Imaginary images are required for complex deconvolution.");
			return showDialog();
		}
		
		// input dialog appears if user does not want to calculate the snr
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
			stacks = new File(stack_path_phase);
			stack_list_phase = stacks.list();
		}
		
		if (!decon_hyper && decon_choice == "Complex (Rectangular)") {
			stack_path = diu.getDirectory("Please select the folder of real stacks:");
			stacks = new File(stack_path);
			stack_list = stacks.list();
			
			stack_path_phase = diu.getDirectory("Please select the folder of imaginary stacks:");
			stacks = new File(stack_path_phase);
			stack_list_phase = stacks.list();
		}
		
		// get desired save directory
		if (save_files) {
			save_path = diu.getDirectory("Select the save directory:");
			save_path += "Deconvolved";
			new File(save_path).mkdirs();

			// check if system uses '/' or '\'
			if (save_path.indexOf('\\') >= 0) 
				divisor = "\\";
			else
				divisor= "/";
			
			save_path += divisor;
			
			// create appropriate folders for deconvolved images
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
		image_amp = WindowManager.getImage(diu.getImageTitle(amp_selection));
		frames = image_amp.getNFrames();
		
		PSF_amp = WindowManager.getImage(diu.getImageTitle(PSF_amp_selection));
		width = PSF_amp.getProcessor().getWidth();
		height = PSF_amp.getProcessor().getHeight();
		slices = PSF_amp.getNSlices();
		
		if (getSNR) {
			// get signal-to-noise through user input
			Noise_NP nnp = new Noise_NP();
			float noiseDev = nnp.getNoise(image_amp);
			float signal = nnp.getSignal(image_amp);
			SNR = signal / noiseDev;
		}
		
		IJ.showStatus("Preprocessing...");
		
		// convert image stacks to matrices
		psfMat = diu.getMatrix3D(PSF_amp);
		cal = PSF_amp.getCalibration();
		
		// get imaginary/phase PSF image if doing complex deconvolution
		if (decon_choice != "Standard") {
			PSF_phase = WindowManager.getImage(diu.getImageTitle(PSF_phase_selection));
			psfPhaseMat = diu.getMatrix3D(PSF_phase);
		}
		
		// normalize PSF matrix accordingly
		if (normalizePSF && decon_choice != "Complex (Rectangular)")
			diu.normalize(psfMat);
		if (normalizePSF && decon_choice == "Complex (Rectangular)")
			diu.normalize(psfMat, psfPhaseMat);
			
		// decide which deconvolution procedure to follow based on user preferences
		if (decon_hyper) {
			// get imaginary/phase image if doing complex deconvolution
			if (decon_choice != "Standard") {
				image_phase = WindowManager.getImage(diu.getImageTitle(phase_selection));
				phaseMat = diu.getMatrix4D(image_phase);
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
	
	// save frames from a hyperstack
	public void save_from_hyperstack() {
		ampMat = diu.getMatrix4D(image_amp);
		Wiener_Utils wu = new Wiener_Utils(width, height, slices, frames, 1/SNR, intensity);
		IJ.showStatus("Deconvolving hyperstack...");
		
		// deconvolve using proper strategy
		if (decon_choice == "Standard")
			wu.deconvolve(ampMat, psfMat, get_error);
		else if (decon_choice == "Complex (Polar)")
			wu.deconvolve(ampMat, phaseMat, psfMat, psfPhaseMat, get_error, "Polar");
		else
			wu.deconvolve(ampMat, phaseMat, psfMat, psfPhaseMat, get_error, "Rectangular");
		
		// loop over the frames of the deconvolved image and save them in the appropriate folders
		IJ.showStatus("Saving images...");
		for (int i = 0; i < frames; i++) {
			ImagePlus tempImg = diu.reassign(wu.imgComplex[i], choice, Integer.toString(i));
			tempImg.setCalibration(cal);
			
			if (decon_choice == "Standard")
				IJ.saveAsTiff(tempImg, save_path + Integer.toString(i) + ".tif");
			else if (decon_choice == "Complex (Polar)") {
				IJ.saveAsTiff(tempImg, save_path + "Amplitude" + divisor + Integer.toString(i) + ".tif");
				
				tempImg = diu.reassign(wu.imgPhase[i], choice, Integer.toString(i));
				tempImg.setCalibration(cal);
				IJ.saveAsTiff(tempImg, save_path + "Phase" + divisor + Integer.toString(i) + ".tif");
			}
			else {
				IJ.saveAsTiff(tempImg, save_path + "Real" + divisor + Integer.toString(i) + ".tif");
				
				tempImg = diu.reassign(wu.imgPhase[i], choice, Integer.toString(i));
				tempImg.setCalibration(cal);
				IJ.saveAsTiff(tempImg, save_path + "Imaginary" + divisor + Integer.toString(i) + ".tif");
			}				
		}
	}
	
	// save by frames from images stored in a folder
	public void save_from_files() {
		ampMat = new float[1][slices][height][width];
		if (decon_choice != "Standard")
			phaseMat = new float[1][slices][height][width];
		
		Wiener_Utils wu = new Wiener_Utils(width, height, slices, 1, 1/SNR, intensity);
		
		// loop over images in stack
		for (int i = 0; i < stack_list.length; i++) {
			IJ.showStatus("Processing frame " + Integer.toString(i + 1) + " of " + Integer.toString(stack_list.length) + "...");
			ImagePlus tempImg = IJ.openImage(stack_path + stack_list[i]);
			ampMat = diu.getMatrix4D(tempImg);
			tempImg.close();
			
			// deconvolve and save
			if (decon_choice == "Standard") {
				wu.deconvolve(ampMat, psfMat, get_error);
				
				tempImg = diu.reassign(wu.imgComplex, choice, Integer.toString(i));
				tempImg.setCalibration(cal);
				IJ.saveAsTiff(tempImg, save_path + Integer.toString(i) + ".tif");
				tempImg.close();
			}
			else if (decon_choice == "Complex (Polar)") {
				// get corresponding phase image
				ImagePlus phaseImg = IJ.openImage(stack_path_phase + stack_list_phase[i]);
				phaseMat = diu.getMatrix4D(phaseImg);
				phaseImg.close();
				
				wu.deconvolve(ampMat, phaseMat, psfMat, psfPhaseMat, get_error, "Polar");
				
				tempImg = diu.reassign(wu.imgComplex, choice, Integer.toString(i));
				tempImg.setCalibration(cal);
				IJ.saveAsTiff(tempImg, save_path + "Amplitude" + divisor + Integer.toString(i) + ".tif");
				
				tempImg = diu.reassign(wu.imgPhase, choice, Integer.toString(i));
				tempImg.setCalibration(cal);
				IJ.saveAsTiff(tempImg, save_path + "Phase" + divisor + Integer.toString(i) + ".tif");
				tempImg.close();
			}
			else {
				// get corresponding complex image
				ImagePlus imImg = IJ.openImage(stack_path_phase + stack_list_phase[i]);
				phaseMat = diu.getMatrix4D(imImg);
				imImg.close();
				
				wu.deconvolve(ampMat, phaseMat, psfMat, psfPhaseMat, get_error, "Rectangular");
				
				tempImg = diu.reassign(wu.imgComplex, choice, Integer.toString(i));
				tempImg.setCalibration(cal);
				IJ.saveAsTiff(tempImg, save_path + "Real" + divisor + Integer.toString(i) + ".tif");
				
				tempImg = diu.reassign(wu.imgPhase, choice, Integer.toString(i));
				tempImg.setCalibration(cal);
				IJ.saveAsTiff(tempImg, save_path + "Imaginary" + divisor + Integer.toString(i) + ".tif");
				tempImg.close();
			}		
		}
	}
	
	// open a deconvolved hyperstack from a hyperstack
	public void show_from_hyperstack() {
		ampMat = diu.getMatrix4D(image_amp);
		Wiener_Utils wu = new Wiener_Utils(width, height, slices, frames, 1/SNR, intensity);
		IJ.showStatus("Deconvolving hyperstack...");
		
		if (decon_choice == "Standard") {
			wu.deconvolve(ampMat, psfMat, get_error);
			
			IJ.showStatus("Constructing result...");
			ImagePlus tempImage = diu.reassign(wu.imgComplex, choice, "Result");
			tempImage.setCalibration(cal);
		
			tempImage.show();
		}
		
		else if (decon_choice == "Complex (Polar)") {
			wu.deconvolve(ampMat, phaseMat, psfMat, psfPhaseMat, get_error, "Polar");
			
			IJ.showStatus("Constructing result...");
			ImagePlus ampImage = diu.reassign(wu.imgComplex, choice, "Amplitude");
			ampImage.setCalibration(cal);
			ampImage.show();
			
			ImagePlus phaseImage = diu.reassign(wu.imgPhase, choice, "Phase");
			phaseImage.setCalibration(cal);
			phaseImage.show();
		}
		
		else {
			wu.deconvolve(ampMat, phaseMat, psfMat, psfPhaseMat, get_error, "Rectangular");
			
			IJ.showStatus("Constructing result...");
			ImagePlus reImage = diu.reassign(wu.imgComplex, choice, "Real");
			reImage.setCalibration(cal);
			reImage.show();
			
			ImagePlus imImage = diu.reassign(wu.imgPhase, choice, "Imaginary");
			imImage.setCalibration(cal);
			imImage.show();
		}	
		
		if (get_error)
			IJ.showMessage("Error: " + Float.toString(wu.error * 100) + "%");
	}
	
	// open a deconvolved hyperstack from saved images
	public void show_from_files() {
		imgMat = new float[stack_list.length][slices][height][width];
		ampMat = new float[1][slices][height][width];
		
		if (decon_choice != "Standard") {
			imgMatPhase = new float[stack_list.length][slices][height][width];
			phaseMat = new float[1][slices][height][width];
		}
		
		Wiener_Utils wu = new Wiener_Utils(width, height, slices, 1, 1/SNR, intensity);
		
		// loop through frames in folder and deconvolve
		for (int i = 0; i < stack_list.length; i++) {
			IJ.showStatus("Processing frame " + Integer.toString(i + 1) + " of " + Integer.toString(stack_list.length) + "...");
			ImagePlus tempImg = IJ.openImage(stack_path + stack_list[i]);
			ampMat = diu.getMatrix4D(tempImg);
			tempImg.close();
			
			// put deconvolved frame in ith slot of hyperstack matrix
			if (decon_choice == "Standard") {
				wu.deconvolve(ampMat, psfMat, get_error);
				imgMat[i] = wu.imgComplex[0];
				tempImg.close();
			}
			else if (decon_choice == "Complex (Polar)") {
				ImagePlus phaseImg = IJ.openImage(stack_path_phase + stack_list_phase[i]);
				phaseMat = diu.getMatrix4D(phaseImg);
				phaseImg.close();
				
				wu.deconvolve(ampMat, phaseMat, psfMat, psfPhaseMat, get_error, "Polar");
				imgMat[i] = wu.imgComplex[0];
				imgMatPhase[i] = wu.imgPhase[0];
			}
			else {
				ImagePlus imImg = IJ.openImage(stack_path_phase + stack_list_phase[i]);
				phaseMat = diu.getMatrix4D(imImg);
				imImg.close();
				
				wu.deconvolve(ampMat, phaseMat, psfMat, psfPhaseMat, get_error, "Rectangular");
				imgMat[i] = wu.imgComplex[0];
				imgMatPhase[i] = wu.imgPhase[0];
			}		
		}
		
		// show final images
		if (decon_choice == "Standard") {
			ImagePlus tempImage = diu.reassign(imgMat, choice, "Result");
			tempImage.setCalibration(cal);

			tempImage.show();
		}
		else if (decon_choice == "Complex (Polar)") {
			ImagePlus amp = diu.reassign(imgMat, choice, "Amplitude");
			amp.setCalibration(cal);
			amp.show();
			
			ImagePlus phase = diu.reassign(imgMatPhase, choice, "Phase");
			phase.setCalibration(cal);
			phase.show();
		}
		else {
			ImagePlus reImage = diu.reassign(imgMat, choice, "Real");
			reImage.setCalibration(cal);
			reImage.show();
			
			ImagePlus imImage = diu.reassign(imgMatPhase, choice, "Imaginary");
			imImage.setCalibration(cal);
			imImage.show();
		}
		
		if (get_error)
			IJ.showMessage("Error: " + Float.toString(wu.error * 100) + "%");
	}

	
	public void showAbout() {
		IJ.showMessage("DeconvolveImage",
			"Deconvolves DHM images using the Wiener filter."
		);
	}
}