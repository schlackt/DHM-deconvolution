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
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;
import java.text.DecimalFormat;


public class Get_Error implements PlugInFilter {
	protected ImagePlus image_amp;
	protected ImagePlus image_phase;
	protected ImagePlus PSF_amp;
	protected ImagePlus PSF_phase;
	protected ImagePlus orig_amp;
	protected ImagePlus orig_phase;
	private String amp_selection;
	private String phase_selection;
	private String PSF_amp_selection;
	private String PSF_phase_selection;
	private String orig_amp_selection;
	private String orig_phase_selection;
	private String style;
	
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
	
	private boolean showDialog() {
		String[] decon_choices = {"Standard", "Complex (Polar)", "Complex (Rectangular)"};
		String[] image_list = diu.imageList();
		GenericDialog gd = new GenericDialog("Error Setup");
		gd.addChoice("Deconvolution style: ", decon_choices, "Standard");
		gd.addChoice("Deconvolved amplitude/real image: ", image_list, image_list[image_list.length - 1]);
		gd.addChoice("Deconvolved phase/imaginary image: ", image_list, image_list[image_list.length - 1]);
		gd.addChoice("PSF amplitude/real image: ", image_list, image_list[image_list.length - 1]);
		gd.addChoice("PSF phase/imaginary image: ", image_list, image_list[image_list.length - 1]);
		gd.addChoice("Original amplitude/real image: ", image_list, image_list[image_list.length - 1]);
		gd.addChoice("Original phase/imaginary image: ", image_list, image_list[image_list.length - 1]);
		
		
		gd.showDialog();
		if (gd.wasCanceled())
			return false;
		
		style = gd.getNextChoice();
		amp_selection = gd.getNextChoice();
		phase_selection = gd.getNextChoice();
		PSF_amp_selection = gd.getNextChoice();
		PSF_phase_selection = gd.getNextChoice();
		orig_amp_selection = gd.getNextChoice();
		orig_phase_selection = gd.getNextChoice();
		
		return true;
	}
	
	public void process(ImageProcessor ip) {
		image_amp = WindowManager.getImage(diu.getImageTitle(amp_selection));
		PSF_amp = WindowManager.getImage(diu.getImageTitle(PSF_amp_selection));
		orig_amp = WindowManager.getImage(diu.getImageTitle(orig_amp_selection));
		
		// convert image stacks to matrices
		float[][][][] imgMat = diu.getMatrix4D(image_amp);
		float[][][][] imgMatOld = diu.getMatrix4D(orig_amp);
		float[][][] psfMat = diu.getMatrix3D(PSF_amp);
		
		double err = 0;
		if (style == "Standard") {
			psfMat = diu.toFFTform(psfMat);
			imgMatOld = diu.toFFTform(imgMatOld);
			imgMat = diu.toFFTform(imgMat);
			err = diu.getError(imgMat, imgMatOld, psfMat);
		}
		else {
			image_phase = WindowManager.getImage(diu.getImageTitle(phase_selection));
			float[][][][] imgMatPhase = diu.getMatrix4D(image_phase);
			
			PSF_phase = WindowManager.getImage(diu.getImageTitle(PSF_phase_selection));
			float[][][] psfPhase = diu.getMatrix3D(PSF_phase);
			
			orig_phase = WindowManager.getImage(diu.getImageTitle(orig_phase_selection));
			float[][][][] imgMatOldPhase = diu.getMatrix4D(orig_phase);
			
			if (style == "Complex (Polar)") {
				imgMat = diu.toFFTform(imgMat, imgMatPhase);
				imgMatOld = diu.toFFTform(imgMatOld, imgMatOldPhase);
				psfMat = diu.toFFTform(psfMat, psfPhase);
			}
			else {
				imgMat = diu.toFFTformRect(imgMat, imgMatPhase);
				imgMatOld = diu.toFFTformRect(imgMatOld, imgMatOldPhase);
				psfMat = diu.toFFTformRect(psfMat, psfPhase);
			}
			
			err = diu.getError(imgMat, imgMatOld, psfMat);	
		}
		DecimalFormat errFormat = new DecimalFormat("###0.00");
		IJ.showMessage("Error: " + errFormat.format(err * 100) + "%");
	}
	
	public void showAbout() {
		IJ.showMessage("DeconvolveImage",
			"Deconvolves DHM images using the Wiener filter."
		);
	}
}