DHM Deconvolution Plugin
========================

This plugin is meant to deconvolve reconstructed DHM images. It contains eight subplugins:
Make Point Image, Resize PSF, Make Hyperstack, Get Error, Convert Images, Wiener Filter, Iterative Deconvolution, and ER-Decon.
Make Point Image allows the user to create a simple image that contains a central dot.
This plugin helps the user create simulated point spread functions (PSFs). Resize PSF allows the user to take an arbitrarily sized PSF and put it into the desired
size for deconvolution. Make Hyperstack can be used to reformat images so they are compatible with the deconvolution plugins. Get Error computes the percent error of a deconvolved
image, and Convert Images allows the user to convert complex images from polar to rectangular form (and vice versa). Wiener
Filter deconvolves images using the Wiener method, Iterative Deconvolution deconvolves using an iterative procedure, and ER-Decon deconvolves
images using entropy regularization. Each of these plugins are described in greater detail
below.

## Make Point Image

The Make Point Image plugin creates simple images with user-defined points in the
center. These images can then be reconstructed in order to create a PSF. The plugin has
three inputs:
* **Reference image:** Image from which the dimensions and experimental values are taken.
* **Point radius (px):** Desired radius of the point in pixels.
* **Use experimental values?:** If checked, use min/max of opened image for pixel
values rather than the default 0/255.

The point is drawn as close to the center as possible, and the plugin outputs a 32-bit image.

## Resize PSF

The deconvolution strategies used here require that the PSF has the same dimensions as
the blurred image. In most cases, however, experimentally determined PSFs are cropped
so that they are smaller than the blurred images. This plugin resizes PSFs that are
smaller in width and/or height than the blurred image. The PSF must have the same number
of slices as the blurred image. There are four inputs:
* **Image to be resized:** PSF to be resized.
* **New height (px):** Desired height in pixels of the resized PSF.
* **New width (px):** Desired width in pixels of the resized PSF.
* **Fill randomly?:** If checked, empty regions are filled with random values selected from
the PSF's border. If unchecked, empty regions are filled with the median of the PSF's border.

The plugin centers the original PSF as much as possible, and then fills the empty regions
either randomly or with a median. Once completed, the resized PSF will be displayed.

## Make Hyperstack

This plugin takes data saved using the DHM Reconstruction plugin and reformats it
so that it is compatible with the deconvolution plugins. The plugin assumes a specific file
structure, and it will not function properly if the data do not follow this structure.

To use this plugin to construct a single four-dimensional image (x, y, z, and t) from a
series of two-dimensional images, the data must be stored according to the following rules:
1. Images in the same z-plane must be stored in the same folder. This folder’s name
is the plane’s z-value, with three decimal places. For example, all images taken at
z = -150 um would be stored in a folder named “-150.000”.
2. All z-plane folders must be stored in the same folder.
3. Time steps are denoted by a 5-digit frame number. The frame number includes leading
zeroes and starts at 00001.
4. Every image’s filename must contain its frame number. The frame number must appear
at either the beginning of the filename (e.g. “00001_holo.tif”) or the end of the filename
(e.g. “holo_00001.tif”).
5. Any prefixes or suffixes to the frame number (see examples above) must be uniform
among *all* images.

The plugin has 13 inputs:
* **Reference image:** Image from which all relevant dimensions are taken.
* **Axial spacing (o.u.):** Space between z-planes as defined during reconstruction. This
should be given in the original units (o.u.) used in reconstruction.
* **Initial z (o.u.):** Desired starting slice for the 4D image, given in the image’s original
units.
* **Final z (o.u.):** Desired ending slice for the 4D image, given in the image’s original
units.
* **Initial frame:** Desired starting frame for the 4D image.
* **Final frame:** Desired ending frame for the 4D image.
* **Filetype:** File type of the 2D images.
* **Filename type:** Dropbox to select whether the filenames contain a prefix, suffix, or
just the frame number.
  * **Prefix/Suffix:** Prefix or suffix of the filenames, if they have one.
* **Output image:** Dropbox to select the output image type (8-, 16-, or 32-bit).
* **Save by frames?:** If checked, the plugin will save each frame's stack separately. The
stacks will be named by their frame number.
  * **Save directory** Prompt to select the directory in which to save the stacks. A new
  folder named `Stacks` is created in this directory, and all stacks are saved in that folder.
* **Image directory:** Prompt to select the folder containing all z-plane folders.

After all of the inputs are entered, the plugin will construct the corresponding
hyperstack or save each frame's stack under the specified directory. If an image
is not found, the plugin will show a message containing the z value and frame number
of the missing image, along with the filepath that the program attempted to open. The
plugin will then abort.

## Get Error

This plugin computes the percent error of a deconvolved image. This is accomplished by
convolving the deblurred image with the PSF and comparing the result with the original
deblurred image. The percent error of the image is given as the mean percent error of
each pixel. There are seven inputs:
* **Deconvolution style:** Dropbox to select how the image was deconvolved. This determines
which of the following inputs are required. "Standard" means deconvolution without any phase/imaginary
information. "Complex (Polar)" means deconvolution using amplitude and phase images, and "Complex (Rectangular)"
means deconvolution using real and imaginary images.
* **Deconvolved amplitude/real image:** Dropbox to select the deconvolved amplitude/real image. Always required.
* **Deconvolved phase/imaginary image:** Dropbox to select the deconvolved phase/imaginary image. Only required if **Deconvolution style**
is not "Standard".
* **PSF amplitude/real image:** Dropbox to select the PSF amplitude/real image. Always required.
* **PSF phase/imaginary image:** Dropbox to select the PSF phase/imaginary image. Only required if **Deconvolution style**
is not "Standard".
* **Original amplitude/real image:** Dropbox to select the original (pre-deconvolution) amplitude/real image. Always required.
* **Original phase/imaginary image:** Dropbox to select the original phase/imaginary image. Only required if **Deconvolution style**
is not "Standard".

Once the inputs are entered, the plugin calculates the percent
error. When it is done, the error will be displayed in a dialog box.

## Wiener Filter

This plugin implements the Wiener deconvolution method, which amounts to dividing
out the PSF in Fourier space. The plugin can either deconvolve currently open images
or all images in a specified directory. The plugin will work with 4D hyperstacks and 3D stacks.
There are 17 inputs:
* **Output image:** Dropbox to select the output image type (8-, 16-, or 32-bit).
* **Deconvolution style:** Dropbox to select how the image will be deconvolved. This determines
which of the following inputs are required. "Standard" means deconvolution without any phase/imaginary
information. "Complex (Polar)" means deconvolution using amplitude and phase images, and "Complex (Rectangular)"
means deconvolution using real and imaginary images.
* **Amplitude/real image:** Dropbox to select the amplitude/real image to be deconvolved. Always required if deconvolving from open images.
* **Phase/imaginary image:** Dropbox to select the phase/imaginary image to be deconvolved. Only required if **Deconvolution style**
is not "Standard" when deconvolving from open images.
* **PSF amplitude/real image:** Dropbox to select the PSF amplitude/real image. Always required if deconvolving from open images.
* **PSF phase/imaginary image:** Dropbox to select the PSF phase/imaginary image. Only required if **Deconvolution style**
is not "Standard" when deconvolving from open images.
* **Get SNR?:** If checked, the plugin will prompt the user to define the signal-to-noise
by drawing regions of interest. If unchecked, the plugin will prompt the user to enter a
custom number.
  * **Noise:** Prompt to select a noisy region of the open image.
  * **Signal:** Prompt to select a signal in the open image.
  * **Beta:** Prompt to enter custom number to avoid division by zero if "Get SNR?"
  is unchecked. It should be small but nonzero.
* **Normalize PSF?:** If checked, the PSF will be normalized so that all of its pixels add
to 1. If the PSF is complex, it is normalized so that all amplitude values add to 1.
* **Use intensity maps?** If checked, the plugin will calculate intensity and deconvolve the intensity images.
* **Display error?:** If checked, the plugin will display the error of the deblurred image in a
dialog box after deconvolution.
* **Deconvolve from files?** If unchecked, the plugin will deconvolve the images selected above.
If checked, the plugin will prompt the user for the directory where the image stacks are stored.
	* **Stack Directory:** Prompt to select the folder in which the stacks to be deconvolved are stored.
	This folder should *only* contain stacks.
* **Save by frame?** If checked, the plugin will prompt the user to select the directory in which to
save deconvolved images. The images are stored as stacks and ordered by frame in a folder named `Deconvolved`.
If unchecked, the plugin will open a hyperstack when deconvolution is complete.
	* **Save Directory:** Prompt to select the directory in which to save deconvolved frames. A folder
	named `Deconvolved` will be created in this directory, and deconvolved images will be placed there.

Once the inputs are entered, a dialog will appear asking the user to select a noisy region of
the blurred image (if "Get Signal-to-Noise" was checked). This can be done by drawing a ROI
of any shape around a noisy area and selecting “OK.” Then, a similar dialog will appear asking
the user to draw another ROI around a region that contains a signal. The plugin will then carry
out the deconvolution and open the deconvolved image (if the user is not saving by frame).

## Iterative Deconvolution

This plugin implements the deconvolution strategy developed by Latychevskaia+ 2010 \[1\], which is designed
to work with complex data more effectively than the Wiener filter. The plugin can either deconvolve currently open images
or all images in a specified directory. The plugin will work with 4D hyperstacks and 3D stacks. There are 17 inputs:
* **Output image:** Dropbox to select the output image type (8-, 16-, or 32-bit).
* **Deconvolution style:** Dropbox to select how the image will be deconvolved. This determines
which of the following inputs are required. "Standard" means deconvolution without any phase/imaginary
information. "Complex (Polar)" means deconvolution using amplitude and phase images, and "Complex (Rectangular)"
means deconvolution using real and imaginary images.
* **Amplitude/real image:** Dropbox to select the amplitude/real image to be deconvolved. Always required if deconvolving from open images.
* **Phase/imaginary image:** Dropbox to select the phase/imaginary image to be deconvolved. Only required if **Deconvolution style**
is not "Standard" when deconvolving from open images.
* **PSF amplitude/real image:** Dropbox to select the PSF amplitude/real image. Always required if deconvolving from open images.
* **PSF phase/imaginary image:** Dropbox to select the PSF phase/imaginary image. Only required if **Deconvolution style**
is not "Standard" when deconvolving from open images.
* **Iterations:** Number of iterations to perform.
* **Get SNR?:** If checked, the plugin will prompt the user to define the signal-to-noise
by drawing regions of interest. If unchecked, the plugin will prompt the user to enter a
custom number.
  * **Noise:** Prompt to select a noisy region of the open image.
  * **Signal:** Prompt to select a signal in the open image.
  * **Beta:** Prompt to enter custom number to avoid division by zero if "Get SNR?"
  is unchecked. It should be small but nonzero.
* **Normalize PSF?:** If checked, the PSF will be normalized so that all of its pixels add
to 1. If PSF is complex, it is normalized so that all amplitude values add to 1.
* **Deconvolve from files?** If unchecked, the plugin will deconvolve the images selected above.
If checked, the plugin will prompt the user for the directory where the image stacks are stored.
	* **Stack Directory:** Prompt to select the folder in which the stacks to be deconvolved are stored.
	This folder should *only* contain stacks.
* **Save by frame?** If checked, the plugin will prompt the user to select the directory in which to
save deconvolved images. The images are stored as stacks and ordered by frame in a folder named `Deconvolved`.
If unchecked, the plugin will open a hyperstack when deconvolution is complete.
	* **Save Directory:** Prompt to select the directory in which to save deconvolved frames. A folder
	named `Deconvolved` will be created in this directory, and deconvolved images will be placed there.
* **Plot errors?** If checked, the plugin will display a plot of error vs. iteration number when complete. Error plots are saved in the `Deconvolved`
folder if **Save by frame?** is checked.

Once the inputs are entered, a dialog will appear asking the user to select a noisy region of
the blurred image (if "Get Signal-to-Noise" was checked). This can be done by drawing a ROI
of any shape around a noisy area and selecting “OK.” Then, a similar dialog will appear asking
the user to draw another ROI around a region that contains a signal. The plugin will then carry
out the deconvolution and open the deconvolved image (if the user is not saving by frame).

## ER-Decon

This plugin implements the deconvolution strategy developed by Arigovindan+ 2013 \[2\].
The plugin can either deconvolve currently open images or all images in a specified directory.
The plugin will work with 4D hyperstacks and 3D stacks. The blurred image can be either 3D or 4D. There are 16 inputs:
* **Output image:** Dropbox to select the output image type (8-, 16-, or 32-bit).
* **Deconvolution style:** Dropbox to select how the image will be deconvolved. This determines
which of the following inputs are required. "Standard" means deconvolution without any phase/imaginary
information. "Complex (Polar)" means deconvolution using amplitude and phase images, and "Complex (Rectangular)"
means deconvolution using real and imaginary images.
* **Amplitude/real image:** Dropbox to select the amplitude/real image to be deconvolved. Always required if deconvolving from open images.
* **Phase/imaginary image:** Dropbox to select the phase/imaginary image to be deconvolved. Only required if **Deconvolution style**
is not "Standard" when deconvolving from open images.
* **PSF amplitude/real image:** Dropbox to select the PSF amplitude/real image. Always required if deconvolving from open images.
* **PSF phase/imaginary image:** Dropbox to select the PSF phase/imaginary image. Only required if **Deconvolution style**
is not "Standard" when deconvolving from open images.
* **Smoothness Factor:** Free parameter that affects the smoothness of the output image.
* **Nonlinearity Factor:** Free parameter that affects the restoration of weak intensities.
* **# Iterations:** Number of iterations to be performed.
* **Lateral Spacing (o.u.):** Pixel size in the original units of reconstruction.
* **Axial Spacing (o.u.):** Size between z-planes in the original units of reconstruction.
* **Normalize PSF?:** If checked, the PSF will be normalized so that all of its pixels add
to 1. If the PSF is complex, it is normalized so that all amplitude values add to 1.
* **Deconvolve from files?** If unchecked, the plugin will deconvolve the images selected above.
If checked, the plugin will prompt the user for the directory where the image stacks are stored.
	* **Stack Directory:** Prompt to select the folder in which the stacks to be deconvolved are stored.
	This folder should *only* contain stacks.
* **Save by frame?** If checked, the plugin will prompt the user to select the directory in which to
save deconvolved images. The images are stored as stacks and ordered by frame in a folder named `Deconvolved`.
If unchecked, the plugin will open a hyperstack when deconvolution is complete.
	* **Save Directory:** Prompt to select the directory in which to save deconvolved frames. A folder
	named `Deconvolved` will be created in this directory, and deconvolved images will be placed there.

Ideal values for the smoothness factor and nonlinearity factor will vary based on the input
image, and they may need to be optimized in order to obtain the best results. The number
of iterations will also affect the output image and may need to be optimized. A high number
of iterations will both take a long time to complete and may result in a washed-out image.

The plugin will begin deconvolution after all inputs are entered. This plugin is much more
memory-intensive and time-consuming than the Wiener filter and iterative deconvolution, so it may be less ideal for
massive data sets.

## Installation

To install the plugin, compile with Maven and then put the .jar file into Fiji's `plugins` folder. Two jars
are produced during compilation - one includes dependencies and the other does not. The jar that includes dependencies
is self-sustaining and can be used if Fiji encounters missing class errors.

You can also add the ImageJ update site https://sites.imagej.net/Schlar/. This option will install the smaller jar that does
not include dependencies. This plugin requires the JTransforms package to run properly.

## References

\[1\] Tatiana Latychevskaia, Fabian Gehri, and Hans-Werner Fink. Depth-resolved holographic reconstructions by three-dimensional deconvolution.
*Optics Express*, 18(21):22527-22524, Oct 2010.

\[2\] Muthuvel Arigovindan, Jennifer C. Fung, Daniel Elnatan, Vito Mennella, Yee-Hung Mark
Chan, Michael Pollard, Eric Branlund, John W. Sedat, and David A. Agard. High resolution
restoration of 3D structures from widefield images with extreme low signal-to-noise
ratio. *Proceedings of the National Academy of Science*, 110(43):17344–17349, Oct
2013.
