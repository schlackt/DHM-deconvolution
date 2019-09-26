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

public class Regularization implements PlugInFilter {
	protected ImagePlus image;
	protected ImagePlus PSF;

	private int tildeCount = 1;
	private float smooth;
	private float nonlinearity;
	private int iterations;
	private float lateral_spacing;
	private float axial_spacing;
	private String path;
	private String choice;
	private String stack_path;
	private String stack_path_phase;
	private String save_path;
	private String decon_choice;
	private String divisor;
	private File stacks;
	private String[] stack_list;
	private String[] stack_list_phase;
	private boolean normalizePSF;
	private boolean decon_hyper;
	private boolean save_files;
	private float[][][] psfPhaseMat;
	private float[][][][] imgMat = new float[1][1][1][1];
	private float[][][][] imgMatPhase = new float[1][1][1][1];
	private float[][][][] phaseMat;
	
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
		gd.addNumericField("Smoothness Factor: ", 0.01, 2);
		gd.addNumericField("Nonlinearity Factor: ", 0.001, 3);
		gd.addNumericField("# Iterations: ", 3, 0);
		gd.addNumericField("Lateral Spacing (o.u.): ", 0.178223, 3);
		gd.addNumericField("Axial Spacing (o.u.): ", 10, 0);
		gd.addCheckbox("Normalize PSF?", true);
		gd.addCheckbox("Deconvolve from Hyperstack?", true);
		gd.addCheckbox("Save by Frames?", false);

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		// get entered values
		choice = gd.getNextChoice();
		decon_choice = gd.getNextChoice();
		smooth = (float) gd.getNextNumber();
		nonlinearity = (float) gd.getNextNumber();
		iterations = (int) gd.getNextNumber();
		lateral_spacing = (float) gd.getNextNumber();
		axial_spacing = (float) gd.getNextNumber();
		normalizePSF = gd.getNextBoolean();
		decon_hyper = gd.getNextBoolean();
		save_files = gd.getNextBoolean();
		
		if (choice == "8-bit")
			choice = "GRAY8";
		else if (choice == "16-bit")
			choice = "GRAY16";
		else {
			choice = "GRAY32";
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

			// determine whether system uses '/' or '\'
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

		return true;
	}
	
	public void process(ImageProcessor ip) {
		// get the PSF file path or exit if the user presses "Cancel"
		path = diu.getPath("Select the PSF real or amplitude image:");
		if (path == null) {
			return;
		}
		PSF = IJ.openImage(path);

		float[][][] psfMat = diu.getMatrix3D(PSF);
		Calibration cal = PSF.getCalibration();
		
		// get imaginary/phase component of the PSF
		if (decon_choice != "Standard") {
			path = diu.getPath("Select the PSF imaginary or phase image:");
			PSF = IJ.openImage(path);
			psfPhaseMat = diu.getMatrix3D(PSF);
		}
		
		// normalize PSF appropriately
		if (normalizePSF && decon_choice != "Complex (Rectangular)")
			diu.normalize(psfMat);
		if (normalizePSF && decon_choice == "Complex (Rectangular)")
			diu.normalize(psfMat, psfPhaseMat);
		
		PSF.close();
		
		// put PSF into correct FFT form
		if (decon_choice == "Standard")
			psfMat = diu.toFFTform(psfMat);
		else if (decon_choice == "Complex (Polar)")
			psfMat = diu.toFFTform(psfMat, psfPhaseMat);
		else
			psfMat = diu.toFFTformRect(psfMat, psfPhaseMat);
		
		int decon_loops = 1;
		float[][][][] ampMat = new float[1][1][1][1];
		ImagePlus tempImg = IJ.createHyperStack("blank", 1, 1, 1, 1, 1, 32);
		// convert image stacks to matrices
		if (decon_hyper) {
			ampMat = diu.getMatrix4D(image);
			for (int i = 0; i < ampMat.length; i++)
				diu.linearShift(ampMat[i], 0, 1);
		}
		else
			decon_loops = stack_list.length;
		
		// initialize the regularization
		for (int j = 0; j < decon_loops; j++) {
			if (!decon_hyper) {
				tempImg = IJ.openImage(stack_path + stack_list[j]);
				ampMat = diu.getMatrix4D(tempImg);
				if (decon_choice == "Standard")
					ampMat = diu.toFFTform(ampMat);
				tempImg.close();
				if (decon_choice != "Standard") {
					tempImg = IJ.openImage(stack_path_phase + stack_list_phase[j]);
					if (decon_choice == "Complex (Polar)") {
						ampMat = diu.toFFTform(ampMat, diu.getMatrix4D(tempImg));
						tempImg.close();
					}
					else {
						ampMat = diu.toFFTformRect(ampMat, diu.getMatrix4D(tempImg));
						tempImg.close();
					}
				}
			}
			else {
				if (decon_choice == "Standard")
					ampMat = diu.toFFTform(ampMat);
				else {
					path = diu.getPath("Select the imaginary or phase image:");
					tempImg = IJ.openImage(path);
					phaseMat = diu.getMatrix4D(tempImg);
					
					if (decon_choice == "Complex (Polar)")
						ampMat = diu.toFFTform(ampMat, phaseMat);
					else
						ampMat = diu.toFFTformRect(ampMat, phaseMat);
					
					tempImg.close();
				}
			}
			Regularization_Utils ru = new Regularization_Utils(ampMat, psfMat, lateral_spacing, axial_spacing, smooth, nonlinearity);
			// deconvolve according to the flow chart in Arigovindan+ 2013 (supplementary information)
			for (int i = 0; i < iterations; i++) {
				IJ.showStatus("Processing iteration " + Integer.toString(i+1) + " of " + Integer.toString(iterations) + "...");
			
				ru.get_dMat();
				ru.get_uMat();
			
				ru.get_guessTilde();
				ru.getEnergyMeasure(true);
				float[] tildes = {ru.errorTilde, ru.errorTilde, ru.errorTilde, ru.errorTilde};
				while (!ru.checkTilde()) {
					IJ.showStatus("Tilde check #" + Integer.toString(tildeCount) + "...");
					ru.damping = (float) (0.7 * ru.damping);
					ru.get_guessTilde();
					ru.getEnergyMeasure(true);
					tildes[tildeCount % 4] = ru.errorTilde;
					if (tildes[0] < tildes[1] && tildes[1] < tildes[2] && tildes[2] < tildes[3]) {
						IJ.showMessage("Error seems to be diverging. Please try again with different parameters.");
						return;
					}
					tildeCount += 1;
				}
				tildeCount = 1;
				
				ru.update();
			}
			
			if (decon_choice == "Standard") {
				ampMat = diu.getAmplitudeMat(ru.guess);
				diu.formatIFFT(ampMat);
			}
			else if (decon_choice == "Complex (Polar)") {
				ampMat = diu.getAmplitudeMat(ru.guess);
				phaseMat = diu.getPhaseMat(ru.guess);
				diu.formatIFFT(ampMat);
				diu.formatIFFT(phaseMat);
			}
			else {
				ampMat = diu.getReMat(ru.guess);
				phaseMat = diu.getImMat(ru.guess);
				diu.formatIFFT(ampMat);
				diu.formatIFFT(phaseMat);
			}
			
			if (decon_hyper) {
				if (!save_files) {
					if (decon_choice == "Standard") {
						ImagePlus result = diu.reassign(ampMat, choice, "Result");
						result.setCalibration(cal);	
				
						result.show();
					}
					else if (decon_choice == "Complex (Polar)") {
						ImagePlus amp = diu.reassign(ampMat, choice, "Amplitude");
						amp.setCalibration(cal);
						amp.show();
						
						ImagePlus phase = diu.reassign(phaseMat, choice, "Phase");
						phase.setCalibration(cal);
						phase.show();
					}
					else {
						ImagePlus real = diu.reassign(ampMat, choice, "Real");
						real.setCalibration(cal);
						real.show();
						
						ImagePlus imag = diu.reassign(phaseMat, choice, "Imaginary");
						imag.setCalibration(cal);
						imag.show();
					}
				}
				else {
					if (decon_choice == "Standard") {
						for (int i = 0; i < ampMat.length; i++) {
							ImagePlus result = diu.reassign(ampMat[i], choice, Integer.toString(i));
							result.setCalibration(cal);
							IJ.saveAsTiff(result, save_path + Integer.toString(i) + ".tif");
						}
					}
					else if (decon_choice == "Complex (Polar)") {
						for (int i = 0; i < ampMat.length; i++) {
							ImagePlus amp = diu.reassign(ampMat[i], choice, Integer.toString(i));
							amp.setCalibration(cal);
							IJ.saveAsTiff(amp, save_path + "Amplitude" + divisor + Integer.toString(i) + ".tif");
							
							ImagePlus phase = diu.reassign(phaseMat[i], choice, Integer.toString(i));
							phase.setCalibration(cal);
							IJ.saveAsTiff(phase, save_path + "Phase" + divisor + Integer.toString(i) + ".tif");
						}
					}
					else {
						for (int i = 0; i < ampMat.length; i++) {
							ImagePlus real = diu.reassign(ampMat[i], choice, Integer.toString(i));
							real.setCalibration(cal);
							IJ.saveAsTiff(real, save_path + "Real" + divisor + Integer.toString(i) + ".tif");
							
							ImagePlus imag = diu.reassign(phaseMat[i], choice, Integer.toString(i));
							imag.setCalibration(cal);
							IJ.saveAsTiff(imag, save_path + "Imaginary" + divisor + Integer.toString(i) + ".tif");
						}
					}
				}		
			}
			else {
				if (!save_files) {
					imgMat[j] = ampMat[0];
					if (decon_choice != "Standard")
						imgMatPhase[j] = phaseMat[0];
				}
				else {
					if (decon_choice == "Standard") {
						ImagePlus result = diu.reassign(ampMat[0], choice, Integer.toString(j));
						result.setCalibration(cal);
						IJ.saveAsTiff(result, save_path + Integer.toString(j) + ".tif");
					}
					else if (decon_choice == "Complex (Polar)") {
						ImagePlus amp = diu.reassign(ampMat[0], choice, Integer.toString(j));
						amp.setCalibration(cal);
						IJ.saveAsTiff(amp, save_path + "Amplitude" + divisor + Integer.toString(j) + ".tif");
						
						ImagePlus phase = diu.reassign(phaseMat[0], choice, Integer.toString(j));
						phase.setCalibration(cal);
						IJ.saveAsTiff(phase, save_path + "Phase" + divisor + Integer.toString(j) + ".tif");
					}
					else {
						ImagePlus real = diu.reassign(ampMat[0], choice, Integer.toString(j));
						real.setCalibration(cal);
						IJ.saveAsTiff(real, save_path + "Real" + divisor + Integer.toString(j) + ".tif");
						
						ImagePlus imag = diu.reassign(phaseMat[0], choice, Integer.toString(j));
						imag.setCalibration(cal);
						IJ.saveAsTiff(imag, save_path + "Imaginary" + divisor + Integer.toString(j) + ".tif");
					}
				}
			}
		}
		
		if (!decon_hyper && !save_files) {
			if (decon_choice == "Standard") {
				ImagePlus result = diu.reassign(imgMat, choice, "Result");
				result.setCalibration(cal);	
		
				result.show();
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
				ImagePlus real = diu.reassign(imgMat, choice, "Real");
				real.setCalibration(cal);
				real.show();
				
				ImagePlus imag = diu.reassign(imgMatPhase, choice, "Imaginary");
				imag.setCalibration(cal);
				imag.show();
			}
		}
	}
	
	public void showAbout() {
		IJ.showMessage("DeconvolveImage",
			"Deconvolves DHM images using entropy regularization."
		);
	}
}