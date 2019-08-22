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
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;

import java.text.DecimalFormat;

//import java.io.FileWriter;
//import java.io.IOException;
//import java.util.Arrays;

public class Make_Hyperstack implements PlugInFilter {
	protected ImagePlus image;

	// image property members
	private int width;
	private int height;
	private int slices;
	private int frames;
	private String choice;
	private double spacing;
	private double z_start;
	private double z_final;
	private int frame_start;
	private int frame_final;
	private String directory;
	private String filetype;
	private String prefixType;
	private String divisor;
	private String prefix;
	private String suffix;
	private Deconvolve_Image_Utils diu = new Deconvolve_Image_Utils();

	@Override
	public int setup(String arg, ImagePlus imp) {
		if (arg.equals("about")) {
			showAbout();
			return DONE;
		}

		image = imp;
		return DOES_8G | DOES_16 | DOES_32 | DOES_RGB;
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
		String[] prefixChoices = {"Default (e.g. \"00001.tif\")", "Prefix (e.g. \"xxx00001.tif\")", "Suffix (e.g. \"00001xxx.tif\")"};
		GenericDialog gd = new GenericDialog("Deconvolution Setup");
		gd.addNumericField("Axial Spacing (o.u.): ", 10, 0);
		gd.addNumericField("Initial z (o.u.): ", 0, 0);
		gd.addNumericField("Final z (o.u.): ", 200, 0);
		gd.addNumericField("Initial Frame: ", 1, 0);
		gd.addNumericField("Final Frame: ", 10, 0);
		gd.addStringField("Filetype: ", ".tif");
		gd.addChoice("Filename Type: ", prefixChoices, "Default (e.g. \"00001.tif\")");
		gd.addChoice("Output Image:", choices, "32-bit");

		gd.showDialog();
		if (gd.wasCanceled())
			return false;

		// get entered values
		spacing = gd.getNextNumber();
		z_start = gd.getNextNumber();
		z_final = gd.getNextNumber();
		frame_start = (int) gd.getNextNumber();
		frame_final = (int) gd.getNextNumber();
		filetype = gd.getNextString();
		prefixType = gd.getNextChoice();
		choice = gd.getNextChoice();
		
		// get the directory and determine if the file system uses '\' or '/'
		directory = diu.getDirectory("Select the image directory:");
		if (directory.indexOf('\\') >= 0)
			divisor = "\\";
		else
			divisor = "/";
			
		prefix = "";
		suffix = "";
		
		if (prefixType != "Default (e.g. \"00001.tif\")") {
			if (prefixType == "Prefix (e.g. \"xxx00001.tif\")") 
				prefix = "Prefix";	
			else 
				prefix = "Suffix";
			GenericDialog gd2 = new GenericDialog(prefix + " Setup");
			gd2.addStringField(prefix + ": ", "", 10);
			gd2.showDialog();
			if (gd2.wasCanceled())
				return false;
			
			prefix = "";
			if (prefixType == "Prefix (e.g. \"xxx00001.tif\")") 
				prefix = gd2.getNextString();	
			else 
				suffix = gd2.getNextString();
		}
		
		if (choice == "8-bit")
			choice = "GRAY8";
		else if (choice == "16-bit")
			choice = "GRAY16";
		else 
			choice = "GRAY32";

		return true;
	}
	
	public void process(ImageProcessor ip) {
		int bitdepth = 24;
		if (choice == "GRAY32")
			bitdepth = 32;
		if (choice == "GRAY16")
			bitdepth = 16;
		if (choice == "GRAY8")
			bitdepth = 8;
		
		slices = (int) ((z_final - z_start) / spacing) + 1;
		frames = frame_final - frame_start + 1;
		
		DecimalFormat zFormat = new DecimalFormat("###0.000");
		DecimalFormat frameFormat = new DecimalFormat("00000");
		ImagePlus hyperStack = IJ.createHyperStack("Result", width, height, 1, slices, frames, bitdepth);
		ImageStack hypStack = hyperStack.getStack();
		ImagePlus tempImg;
		ImageStack tempStack;
		String path;
		for (double i = z_start; i <= z_final; i += spacing) {
			for (int j = frame_start; j <= frame_final; j++) {
				if (directory.charAt(directory.length() - 1) == divisor.charAt(0))
					path = directory + zFormat.format(i) + divisor + prefix + frameFormat.format(j) + suffix + filetype;
				else
					path = directory + divisor + zFormat.format(i) + divisor + prefix + frameFormat.format(j) + suffix + filetype;
				try {
					tempImg = IJ.openImage(path);
					tempStack = tempImg.getStack();
				
					for (int k = 0; k < height; k++)
						for (int l = 0; l < width; l++)
							hypStack.setVoxel(l, k, hyperStack.getStackIndex(1, (int)((i-z_start)/spacing) + 1, j-frame_start + 1) - 1, (double)tempStack.getVoxel(l, k, 0));
				}
				catch (NullPointerException ex) {
					IJ.showMessage("Missing image at z = " + zFormat.format(i) + ", frame " + frameFormat.format(j) + ". \nAttempted Directory: " + path);
					return;
				}
				
			}	
		}
		hyperStack.show();
	}
	
	public void showAbout() {
		IJ.showMessage("MakeHyperstack",
			"Creates a hyperstack from images saved using the DHM reconstruction plugin."
		);
	}
}