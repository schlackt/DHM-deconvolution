DHM Deconvolution Plugin
========================

This plugin is meant to deconvolve reconstructed DHM images. It contains six subplugins:
Make Point Image, Create Hyperstack, Wiener Filter, ER Deconvolution, Get Error, and Resize PSF.
Make Point Image allows the user to create a simple image that contains a central dot.
This plugin helps the user create point spread functions (PSFs). Create Hyperstack can be
used to reformat images so they are compatible with the deconvolution plugins. Wiener
Filter deconvolves images using the Wiener method, and ER Deconvolution deconvolves
images using entropy regularization. Get Error computes the percent error of a deconvolved
image. Resize PSF allows the user to take an arbitrarily sized PSF and put it into the desired
size for deconvolution. Each of these plugins are described in greater detail
below.

## Make Point Image

The Make Point Image plugin creates simple images with user-defined points in the
center. These images can then be reconstructed in order to create a PSF. The plugin has
three inputs:
* **Point height (px):** Desired height of the point in pixels.
* **Point width (px):** Desired width of the point in pixels.
* **Use experimental values?:** If checked, use min/max of opened image for pixel
values rather than the default 0/255.

The point is drawn as close to the center as possible. This plugin retrieves the output image’s
height and width from the currently opened image, and it outputs a 32-bit image.

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

The plugin has 9 inputs:
* **Axial Spacing (o.u.):** Space between z-planes as defined during reconstruction. This
should be given in the original units (o.u.) used in reconstruction.
* **Initial z (o.u.):** Desired starting slice for the 4D image, given in the image’s original
units.
* **Final z (o.u.):** Desired ending slice for the 4D image, given in the image’s original
units.
* **Initial Frame:** Desired starting frame for the 4D image.
* **Final Frame:** Desired ending frame for the 4D image.
* **Filetype:** File type of the 2D images.
* **Filename Type:** Dropbox to select whether the filenames contain a prefix, suffix, or
just the frame number.
  * **Prefix/Suffix:** Prefix or suffix of the filenames, if they have one.
* **Output Image:** Dropbox to select the output image type (8-, 16-, or 32-bit).
* **Image Directory:** Prompt to select the folder containing all z-plane folders.

After all of the inputs are entered, the plugin will construct and show the corresponding
hyperstack. If an image is not found, the plugin will show a message containing the z value
and frame number of the missing image, along with the filepath that the program attempted
to open. The plugin will then abort. This plugin retrieves the proper height and width from
the currently open image.

## Wiener Filter

This plugin implements the Wiener deconvolution method, which amounts to dividing
out the PSF in Fourier space. The plugin assumes that the image to be deconvolved is
currently open. This image can either be a 4D hyperstack (if deconvolving in time), or a
single 3D image. There are 10 inputs:
* **Output Image:** Dropbox to select the output image type (8-, 16-, or 32-bit).
* **Invert Images?:** If checked, the plugin will invert the blurred image and PSF before
deconvolving. The result and original image are both inverted again after deconvolution.
* **Get Signal-to-Noise?:** If checked, the plugin will prompt the user to define the SNR
by drawing regions of interest. If unchecked, the plugin will prompt the user to enter a
custom number.
  * **Noise:** Prompt to select a noisy region of the open image.
  * **Signal:** Prompt to select a signal in the open image.
  * **Beta:** Custom number to avoid division by zero if "Get Signal-to-Noise" is unchecked.
  It should be small but nonzero.
* **Normalize PSF?:** If checked, the PSF will be normalized so that all of its pixels add
to 1.
* **Minimize Error?:** If checked, the plugin will attempt to minimize the error of the deblurred
image by adjusting how the PSF is scaled. As currently designed, this feature will only find a
local minima (not necessarily the absolute minimum).
* **Display Error?:** If checked, the plugin will display the error of the deblurred image in a
dialog box after deconvolution.
* **PSF:** Dialog to open the PSF image. This should be a 3D image with the same
dimensions as the image to be deconvolved.

First, the plugin asks for the parameters above. Then, a dialog appears asking the
user to navigate to the PSF image. This should be a 3D image with the same dimensions
as the image to be deconvolved. The central z-plane on both the PSF and blurred image
should be the focal plane.

Once the PSF is selected, a dialog will appear asking the user to select a noisy region of
the blurred image (if "Get Signal-to-Noise" was checked). This can be done by drawing a ROI
of any shape around a noisy area and selecting “OK.” Then, a similar dialog will appear asking
the user to draw another ROI around a region that contains a signal. The plugin will then carry
out the deconvolution and open the deconvolved image.

## ER-Decon

This plugin implements the deconvolution strategy developed by Arigovindan+ 2013 \[1\].
Like the Wiener Filter, this plugin assumes that the image to be deconvolved is currently
open. The blurred image can be either 3D or 4D. There are seven inputs:
* **Output Image:** Dropbox to select the output image type (8-, 16-, or 32-bit).
* **Smoothness Factor:** Free parameter that affects the smoothness of the output image.
* **Nonlinearity Factor:** Free parameter that affects the restoration of weak intensities.
* **# Iterations:** Number of iterations to be performed.
* **Lateral Spacing (o.u.):** Pixel size in the original units of reconstruction.
* **Axial Spacing (o.u.):** Size between z-planes in the original units of reconstruction.
* **PSF:** Dialog to open the PSF image. This should be a 3D image with the same
dimensions as the image to be deconvolved.

Ideal values for the smoothness factor and nonlinearity factor will vary based on the input
image, and they may need to be optimized in order to obtain the best results. The number
of iterations will also affect the output image and may need to be optimized. A high number
of iterations will both take a long time to complete and may result in a washed-out image.

After the first six inputs are entered, a dialog will appear asking the user to select the
PSF image. Again, this should be a 3D image with the same dimensions as the image to
be deconvolved, and the central z-plane on both the PSF and blurred image should be the
focal plane.

Once the PSF is selected, the plugin will begin deconvolution and open the deconvolved
image when complete. This plugin assumes that signal is darker than noise, so the image
should be inverted before deconvolution if that is not the case. This plugin is much more
memory-intensive and time-consuming than the Wiener filter, so it may be less ideal for
massive data sets.

## Get Error

This plugin computes the percent error of a deconvolved image. This is accomplished by
convolving the deblurred image with the PSF and comparing the result with the original
deblurred image. The percent error of the image is given as the mean percent error of
each pixel. The plugin assumes that the deconvolved image is currently open. There are
two inputs:
* **PSF:** Dialog to open the PSF image. This should be the same PSF used to deconvolve
the image.
* **Blurred Image:** Dialog to open the original blurred image.

Once the PSF and original blurred images are opened, the plugin calculates the percent
error. When it is done, the error will be displayed in a dialog box.

## Resize PSF

The deconvolution strategies used here require that the PSF has the same dimensions as
the blurred image. In most cases, however, experimentally determined PSFs are cropped
so that they are smaller than the blurred images. This plugin resizes PSFs that are
smaller in width and/or height than the blurred image. The PSF must have the same number
of slices as the blurred image. The plugin assumes that the PSF to be resized is currently
opened. There are three inputs:
* **New Height (px):** Desired height in pixels of the resized PSF.
* **New Width (px):** Desired width in pixels of the resized PSF.
* **Fill Randomly?:** If checked, empty regions are filled with random values selected from
the PSF's border. If unchecked, empty regions are filled with the median of the PSF's border.

The plugin centers the original PSF as much as possible, and then fills the empty regions
either randomly or with a median. Once completed, the resized PSF will be displayed.

## Installation

To install the plugin, compile with Maven and then put the .jar file into Fiji's `plugins` folder.
You can also add the ImageJ update site https://sites.imagej.net/Schlar/.

## References

\[1\] Muthuvel Arigovindan, Jennifer C. Fung, Daniel Elnatan, Vito Mennella, Yee-Hung Mark
Chan, Michael Pollard, Eric Branlund, John W. Sedat, and David A. Agard. High resolution
restoration of 3D structures from widefield images with extreme low signal-to-noise
ratio. *Proceedings of the National Academy of Science*, 110(43):17344–17349, Oct
2013.
