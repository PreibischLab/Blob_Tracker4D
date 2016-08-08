
package houghandWatershed;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import com.sun.tools.javac.util.Pair;

import drawandOverlay.OverlayLines;
import drawandOverlay.PushCurves;
import ij.ImageJ;
import labeledObjects.Lineobjects;
import labeledObjects.Simpleobject;
import lut.SinCosinelut;
import net.imglib2.PointSampleList;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.stats.Normalize;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgFactory;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;
import peakFitter.LengthDetection;
import peakFitter.Linefitter;
import preProcessing.Kernels;
import preProcessing.Kernels.ProcessingType;
import preProcessing.MedianFilter;
import util.ImgLib2Util;

public class HoughpostWater {

	public static void main(String[] args) throws Exception {

		RandomAccessibleInterval<FloatType> biginputimg = ImgLib2Util
				.openAs32Bit(new File("src/main/resources/Fake_databignosnp.tif"));
		// small_mt.tif image to be used for testing
		// 2015-01-14_Seeds-1.tiff for actual
		// mt_experiment.tif for big testing
		// Fake_databigsnp.tif for fake data with noise
		// small_test.tif for fake test data
		new ImageJ();

		new Normalize();
		FloatType minval = new FloatType(0);
		FloatType maxval = new FloatType(1);

		Normalize.normalize(Views.iterable(biginputimg), minval, maxval);
		ImageJFunctions.show(biginputimg);
		final int ndims = biginputimg.numDimensions();
		// Define the psf of the microscope
		double[] psf = new double[ndims];
		psf[0] = 1.7;
		psf[1] = 1.8;
		final long radius = (long) Math.ceil(Math.sqrt(psf[0] * psf[0] + psf[1] * psf[1]));
		// Initialize empty images to be used later
		RandomAccessibleInterval<FloatType> inputimg = new ArrayImgFactory<FloatType>().create(biginputimg,
				new FloatType());
		RandomAccessibleInterval<FloatType> preinputimg = new ArrayImgFactory<FloatType>().create(biginputimg,
				new FloatType());
		RandomAccessibleInterval<FloatType> penulinputimg = new ArrayImgFactory<FloatType>().create(biginputimg,
				new FloatType());
		RandomAccessibleInterval<FloatType> imgout = new ArrayImgFactory<FloatType>().create(biginputimg,
				new FloatType());
		RandomAccessibleInterval<FloatType> gaussimg = new ArrayImgFactory<FloatType>().create(biginputimg,
				new FloatType());
		// Compute the Sin Cosine lookup table
		SinCosinelut.getTable();
		// Preprocess image

		penulinputimg = Kernels.Meanfilterandsupress(biginputimg, radius);
		preinputimg = Kernels.Preprocess(penulinputimg, ProcessingType.CannyEdge);
		 Kernels.MeanFilter(preinputimg, inputimg, radius);
		

		ImageJFunctions.show(inputimg).setTitle("Preprocessed image");
		ArrayList<Lineobjects> linelist = new ArrayList<Lineobjects>(biginputimg.numDimensions());
		Img<IntType> Intimg = new ArrayImgFactory<IntType>().create(biginputimg, new IntType());
		Pair<Img<IntType>, ArrayList<Lineobjects>> linepair = new Pair<Img<IntType>, ArrayList<Lineobjects>>(Intimg,
				linelist);

		// Do watershedding and Hough

		// Declare minimum length of the line(in pixels) to be detected
		double minlength = 0;

		linepair = PerformWatershedding.DowatersheddingandHough(biginputimg, inputimg, minlength);

		// Overlay detected lines on the image


		double[] final_param = new double[2 * ndims + 1];

		
		// If there is no noise in the data put SNR = 0

		ArrayList<double[]> totalgausslist = new ArrayList<double[]>();

		// Get a rough reconstruction of the line and the list of centroids
		// where psf of the image has to be convolved

		final ArrayList<Simpleobject> simpleobject = new ArrayList<Simpleobject>();
		OverlayLines.GetAlllines(imgout, biginputimg, linepair.fst, linepair.snd,
				simpleobject, radius);

		ImageJFunctions.show(imgout).setTitle("Rough-Reconstruction");

		// Input the image on which you want to do the fitting, along with the
		// labelled image

		Linefitter MTline = new Linefitter(biginputimg, linepair.fst);

		double[] sigma = new double[ndims];
		sigma[0] = psf[0];
		sigma[1] = psf[1];
		for (int index = 0; index < simpleobject.size(); ++index) {

			// Do gradient descent to improve the Hough detected lines

			final_param = MTline.Getfinallineparam(simpleobject.get(index).Label, simpleobject.get(index).slope,
					simpleobject.get(index).intercept,psf, sigma);
			final double[] cordone = { final_param[0], final_param[1] };
			final double[] cordtwo = { final_param[2], final_param[3] };

			double distance = MTline.Distance(cordone, cordtwo);
			
			
			PushCurves.DrawfinalLine(gaussimg,final_param, psf);
			
			
			System.out
					.println(
							"Label: " + simpleobject.get(index).Label + " " + "StartX:" + final_param[0] + " StartY:"
									+ final_param[1] + " " + "EndX:" + final_param[2] + "EndY: " + final_param[3]) ;
					System.out.println( "Slope: "
									+ (final_param[3] - final_param[1]) / (final_param[2] - final_param[0])
									+ "Intercept :"
									+ (final_param[1] - (final_param[3] - final_param[1])
											/ (final_param[2] - final_param[0]) * final_param[0])
									+ "Length: " + distance);
			try { FileWriter writer = new
					  FileWriter("finallengthsbigchange.txt", true); 
			writer.write("StartX:" + final_param[0] + " StartY:"
					+ final_param[1] + " " + "EndX:" + final_param[2] + "EndY: " + final_param[3] + " "
					+ " " + "Slope: "
					+ (final_param[3] - final_param[1]) / (final_param[2] - final_param[0])
					+ "Intercept :"
					+ (final_param[1] - (final_param[3] - final_param[1])
							/ (final_param[2] - final_param[0]) * final_param[0])
					+ "Length: " + distance
					  ); writer.write("\r\n"); writer.close();
			

		}catch (IOException e) { e.printStackTrace(); }
		}
		
		
		ImageJFunctions.show(gaussimg);
		
		
	}
}
		

	

