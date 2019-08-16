package edu.pdx.imagej.deconv;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.process.ImageStatistics;

public class Noise_NP {
	
	public float getNoise(ImagePlus image) {
		WaitForUserDialog dialog = new WaitForUserDialog("Please select a region of noise that has little to no signal.");
		dialog.show();
		Roi roi = image.getRoi();
		ImageStatistics roiStats = roi.getStatistics();
		float stdDev = (float)roiStats.stdDev;
		
		return stdDev;
	}
	
	public float getSignal(ImagePlus image) {
		WaitForUserDialog dialog = new WaitForUserDialog("Please select a region that contains a signal with little to no noise.");
		dialog.show();
		Roi roi = image.getRoi();
		ImageStatistics roiStats = roi.getStatistics();
		float mean = (float)roiStats.mean;
		
		return mean;
	}
}
