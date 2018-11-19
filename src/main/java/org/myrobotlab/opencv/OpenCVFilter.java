/**
 *                    
 * @author grog (at) myrobotlab.org
 *  
 * This file is part of MyRobotLab (http://myrobotlab.org).
 *
 * MyRobotLab is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License 2.0 as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version (subject to the "Classpath" exception
 * as provided in the LICENSE.txt file that accompanied this code).
 *
 * MyRobotLab is distributed in the hope that it will be useful or fun,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License 2.0 for more details.
 *
 * All libraries in thirdParty bundle are subject to their own license
 * requirements - please refer to http://myrobotlab.org/libraries for 
 * details.
 * 
 * Enjoy !
 * 
 * */

package org.myrobotlab.opencv;

import static org.bytedeco.javacpp.opencv_core.IPL_DEPTH_8U;
import static org.bytedeco.javacpp.opencv_core.cvCopy;
import static org.bytedeco.javacpp.opencv_core.cvCreateImage;
import static org.bytedeco.javacpp.opencv_core.cvGetSize;
import static org.bytedeco.javacpp.opencv_imgcodecs.cvLoadImage;
import static org.bytedeco.javacpp.opencv_imgcodecs.imread;

import static org.bytedeco.javacpp.opencv_imgproc.*;
import static org.bytedeco.javacpp.opencv_calib3d.*;
import static org.bytedeco.javacpp.opencv_core.*;
import static org.bytedeco.javacpp.opencv_features2d.*;
import static org.bytedeco.javacpp.opencv_flann.*;
import static org.bytedeco.javacpp.opencv_highgui.*;
import static org.bytedeco.javacpp.opencv_imgcodecs.*;
import static org.bytedeco.javacpp.opencv_ml.*;
import static org.bytedeco.javacpp.opencv_objdetect.*;
import static org.bytedeco.javacpp.opencv_photo.*;
import static org.bytedeco.javacpp.opencv_shape.*;
import static org.bytedeco.javacpp.opencv_stitching.*;
import static org.bytedeco.javacpp.opencv_video.*;
import static org.bytedeco.javacpp.opencv_videostab.*;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.Serializable;
import java.util.ArrayList;

import javax.swing.ImageIcon;
import javax.swing.WindowConstants;

import org.bytedeco.javacpp.opencv_core.CvSize;
import org.bytedeco.javacpp.opencv_core.IplImage;
import org.bytedeco.javacpp.opencv_core.Mat;
import org.bytedeco.javacv.CanvasFrame;
import org.bytedeco.javacv.Frame;
import org.myrobotlab.framework.Service;
import org.myrobotlab.io.FileIO;
import org.myrobotlab.logging.LoggerFactory;
import org.myrobotlab.math.MathUtils;
import org.myrobotlab.net.Http;
import org.myrobotlab.service.OpenCV;
import org.slf4j.Logger;

public abstract class OpenCVFilter implements Serializable {
  private static final long serialVersionUID = 1L;

  public final static Logger log = LoggerFactory.getLogger(OpenCVFilter.class.toString());

  // communal data store
  OpenCVData data;

  final public String name;

  boolean enabled = true;
  boolean displayEnabled = false;
  boolean displayExport = false;
  boolean displayMeta = false;

  /**
   * color of display if any overlay
   */
  transient Color displayColor;

  // FIXME - deprecate - not needed or duplicated or in OpenCV framework
  // pipeline...
  public boolean publishDisplay = false;
  public boolean publishData = true;
  public boolean publishImage = false;

  // input image attributes
  int width;
  int height;
  int channels;

  transient CvSize imageSize;

  public String sourceKey;

  // TODO change name to opencv
  transient protected OpenCV opencv;

  protected transient Boolean running;

  public OpenCVFilter() {
    this(null);
  }

  public OpenCVFilter(String name) {
    if (name == null) {
      this.name = this.getClass().getSimpleName().substring("OpenCVFilter".length());
    } else {
      this.name = name;
    }
  }

  // TODO - refactor this back to single name constructor - the addFilter's new
  // responsiblity it to
  // check to see if inputkeys and other items are valid
  public OpenCVFilter(String filterName, String sourceKey) {
    this.name = filterName;
    this.sourceKey = sourceKey;
  }

  public abstract IplImage process(IplImage image) throws InterruptedException;

  public abstract void imageChanged(IplImage image);

  public void setOpenCV(OpenCV opencv) {
    if (displayColor == null) {
      displayColor = opencv.getColor();
    }
    this.opencv = opencv;
  }

  public OpenCV getOpenCV() {
    return opencv;
  }

  public OpenCVFilter setState(OpenCVFilter other) {
    return (OpenCVFilter) Service.copyShallowFrom(this, other);
  }

  public void invoke(String method, Object... params) {
    opencv.invoke(method, params);
  }

  public void broadcastFilterState() {
    FilterWrapper fw = new FilterWrapper(this.name, this);
    opencv.invoke("publishFilterState", fw);
  }

  public ArrayList<String> getPossibleSources() {
    ArrayList<String> ret = new ArrayList<String>();
    ret.add(name);
    return ret;
  }

  /**
   * when a filter is removed from the pipeline its given a chance to return
   * resourcs
   */
  public void release() {
  }

  protected ImageIcon createImageIcon(String path, String description) {
    java.net.URL imgURL = getClass().getResource(path);
    if (imgURL != null) {
      return new ImageIcon(imgURL, description);
    } else {
      System.err.println("Couldn't find file: " + path);
      return null;
    }
  }

  public void samplePoint(Integer x, Integer y) {
    //
    log.info("Sample point called " + x + " " + y);
  }

  public IplImage setData(OpenCVData data) {
    this.data = data;
    data.setSelectedFilter(name);
    // FIXME - determine source of incoming image ...
    // FIXME - getImage(filter.sourceKey) => if null then use getImage()
    // grab the incoming image ..
    IplImage image = data.getOutputImage(); // <-- getting input from output

    if (image != null && (image.width() != width || image.nChannels() != channels)) {
      width = image.width();
      channels = image.nChannels();
      height = image.height();
      imageSize = cvGetSize(image);
      imageChanged(image);
    }
    return image;
  }

  public void enableDisplay(boolean b) {
    displayEnabled = b;
  }

  public void enable(boolean b) {
    enabled = b;
  }

  // GET THE BUFFERED IMAGE FROM "MY" Iplimage !!!!
  /*
   * public BufferedImage getBufferedImage() { return data.getDisplay(); }
   */

  // GET THE Graphics IMAGE FROM "MY" BufferedImage !!!!
  /*
   * public Graphics2D getGraphics() { return data.getGraphics(); }
   */

  /**
   * method which determines if this filter to process its display TODO - have
   * it also decide if its cumulative display or not
   */
  public void processDisplay() {

    if (enabled && displayEnabled) {
      // TODO - this determines our "source" of image
      // and appends meta data

      // to make a decision about "source" you have to put either
      // "current display" cv.display
      // previous buffered image <== aggregate
      // "input" buffered image ?
      BufferedImage input = null;

      // displayExport displayMeta displayEnabled enabled
      if (displayExport) {
        // FIXME - be direct ! data.data.getBufferedImage(filter.name)
        input = data.getBufferedImage();
      } else {
        // else cumulative display
        input = data.getDisplay();
      }

      if (input != null) {
        Graphics2D graphics = input.createGraphics();

        BufferedImage bi = processDisplay(graphics, input);

        data.put(bi);
        data.putDisplay(bi);
      }
    }
  }

  abstract public BufferedImage processDisplay(Graphics2D graphics, BufferedImage image);

  /**
   * This is NOT the filter's image, but really the output of the previous
   * filter ! to be used as input for "this" filters process method
   * 
   * @return
   */
  public IplImage getImage() {
    return data.getImage();
  }

  /*
   * FIXME - TODO public Mat getMat() { return data.getMat(); }
   */

  public void put(String keyPart, Object object) {
    data.put(keyPart, object);
  }

  /**
   * put'ing all the data into output and/or display
   */
  public void postProcess(IplImage processed) {
    data.put(processed);
  }

  public void saveToFile(String filename, IplImage image) {
    opencv.saveToFile(filename, image);
  }

  public Frame toFrame(Mat image) {
    return opencv.toFrame(image);
  }

  public Frame toFrame(IplImage image) {
    return opencv.toFrame(image);
  }

  public Mat toMat(Frame image) {
    return opencv.toMat(image);
  }

  public Mat toMat(IplImage image) {
    return opencv.toMat(image);
  }

  public IplImage toImage(Frame image) {
    return opencv.toImage(image);
  }

  public IplImage toImage(Mat image) {
    return opencv.toImage(image);
  }

  public void error(String format, Object... args) {
    if (opencv == null) {
      log.error(String.format(format, args));
    } else {
      opencv.error(format, args);
    }
  }

  public void show(final IplImage image, final String title) {
    CanvasFrame canvas = new CanvasFrame(title);
    // canvas.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    canvas.showImage(opencv.toFrame(image));
  }

  public void show(final Mat image, final String title) {
    CanvasFrame canvas = new CanvasFrame(title);
    // canvas.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    canvas.showImage(opencv.toFrame(image));
  }

  public IplImage copy(final IplImage image) {
    IplImage copy = cvCreateImage(cvGetSize(image), image.depth(), image.nChannels());
    cvCopy(image, copy, null);
    return copy;
  }

  static public String getImageFromUrl(String url) {
    String ret = getCacheFile(url);
    if (ret != null) {
      return ret;
    }
    byte[] data = Http.get(url);
    if (data == null) {
      log.error("could not get {}", url);
      return null;
    }
    return putCacheFile(url, data);
  }

  static public String getCacheFile(String url) {
    String path = OpenCV.CACHE_DIR + File.separator + url.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
    File f = new File(path);
    if (f.exists()) {
      return path;
    }
    return null;
  }

  static public String putCacheFile(String url, byte[] data) {
    try {
      String path = OpenCV.CACHE_DIR + File.separator + url.replaceAll("[^a-zA-Z0-9\\.\\-]", "_");
      FileIO.toFile(path, data);
      return path;
    } catch (Exception e) {
      log.error("putCacheFile threw", e);
    }
    return null;
  }

  static public Mat loadMat(String infile) {
    String tryfile = infile;

    if (tryfile.startsWith("http")) {
      tryfile = getImageFromUrl(tryfile);
    }

    // absolute file exists ?
    File f = new File(tryfile);
    if (f.exists()) {
      return read(tryfile); // load alpha
    } else {
      log.warn("could load Mat {}", tryfile);
    }

    // service resources - when jar extracts ?
    tryfile = "resource" + File.separator + infile;
    f = new File(tryfile);
    if (f.exists()) {
      return read(tryfile);
    } else {
      log.warn("could load Mat {}", tryfile);
    }

    // source/ide
    // e.g. src\main\resources\resource\OpenCV
    tryfile = "src" + File.separator + "main" + File.separator + "resources" + File.separator + "resource" + File.separator + OpenCV.class.getSimpleName() + File.separator
        + infile;
    f = new File(tryfile);
    if (f.exists()) {
      return read(tryfile);
    } else {
      log.warn("could load Mat {}", tryfile);
    }

    // src\test\resources\OpenCV
    tryfile = "src" + File.separator + "test" + File.separator + "resources" + File.separator + OpenCV.class.getSimpleName() + File.separator + infile;
    f = new File(tryfile);
    if (f.exists()) {
      return read(tryfile);
    } else {
      log.warn("could load Mat {}", tryfile);
    }

    log.error("could not load Mat {}", infile);
    return null;
  }
  
  static private Mat read(String filename) {
    return imread(filename,  CV_LOAD_IMAGE_UNCHANGED);
  }
  
  static private IplImage load(String filename) {
    return cvLoadImage(filename,  CV_LOAD_IMAGE_UNCHANGED);
  }

  static public IplImage loadImage(String infile) {
    String tryfile = infile;

    if (tryfile.startsWith("http")) {
      tryfile = getImageFromUrl(tryfile);
    }
    
    // absolute file exists ?
    File f = new File(tryfile);
    if (f.exists()) {
      return load(tryfile);
    } else {
      log.warn("could load Mat {}", tryfile);
    }

    // service resources - when jar extracts ?
    tryfile = "resource" + File.separator + infile;
    f = new File(tryfile);
    if (f.exists()) {
      return load(tryfile);
    } else {
      log.warn("could load Mat {}", tryfile);
    }

    // source/ide
    // e.g. src\main\resources\resource\OpenCV
    tryfile = "src" + File.separator + "main" + File.separator + "resources" + File.separator + "resource" + File.separator + OpenCV.class.getSimpleName() + File.separator
        + infile;
    f = new File(tryfile);
    if (f.exists()) {
      return load(tryfile);
    } else {
      log.warn("could load Mat {}", tryfile);
    }

    // src\test\resources\OpenCV
    tryfile = "src" + File.separator + "test" + File.separator + "resources" + File.separator + OpenCV.class.getSimpleName() + File.separator + infile;
    f = new File(tryfile);
    if (f.exists()) {
      return load(tryfile);
    } else {
      log.warn("could load Mat {}", tryfile);
    }

    log.error("could not load Mat {}", infile);
    return null;
  }
  
  /*
  public Mat overlayImage(final Mat background, final Mat foreground, 
      Mat output, int posX , int posY)
    {
      background.copyTo(output);

      // start at the row indicated by location, or at row 0 if location.y is negative.
      for(int y = posY; y < background.rows; ++y)
      {
        int fY = y - location.y; // because of the translation

        // we are done of we have processed all rows of the foreground image.
        if(fY >= foreground.rows)
          break;

        // start at the column indicated by location, 

        // or at column 0 if location.x is negative.
        for(int x = std::max(location.x, 0); x < background.cols; ++x)
        {
          int fX = x - location.x; // because of the translation.

          // we are done with this row if the column is outside of the foreground image.
          if(fX >= foreground.cols)
            break;

          // determine the opacity of the foregrond pixel, using its fourth (alpha) channel.
          double opacity =
            ((double)foreground.data[fY * foreground.step + fX * foreground.channels() + 3])

            / 255.;


          // and now combine the background and foreground pixel, using the opacity, 

          // but only if opacity > 0.
          for(int c = 0; opacity > 0 && c < output.channels(); ++c)
          {
            unsigned char foregroundPx =
              foreground.data[fY * foreground.step + fX * foreground.channels() + c];
            unsigned char backgroundPx =
              background.data[y * background.step + x * background.channels() + c];
            output.data[y*output.step + output.channels()*x + c] =
              backgroundPx * (1.-opacity) + foregroundPx * opacity;
          }
        }
      }
    */

 
}