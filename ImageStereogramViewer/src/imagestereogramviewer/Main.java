/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package imagestereogramviewer;

//TODO: remove dependency on proprietary API
import com.sun.imageio.plugins.gif.GIFImageMetadata; //proprietary

import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 *
 * @author Edgar Villani Peres (seizonsenryaku@github)
 */
public class Main implements Runnable {

    /**
     * Creates new form MainFrame
     */
    private static BufferedImage outputImg;
    private static WritableRaster outputRaster;
    private static int[][] originalPixels;
    private static int[] outputPixels = null;
    private static int imgWidth, imgHeight;
    private static int offset;
    private static JFrame frame;
    private static final Object offsetMutex = new Object();
    private final static int targetFPS = 60;
    private final static int targetTime = 1000 / targetFPS;
    private static int frameNum = 0;
    private static int frameDelays[];
    private static int frameDelayAccumulator = 0;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("ImageStereogramViewer 0.2");
            System.out.println("Author: "
                    + "Edgar Villani Peres - edgarvperes@gmail.com");
            System.out.println("Use image filename as command line argument.");
            return;
        }
        final String path = args[0];

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                createAndShowGUI(path);
            }
        });

    }

    private static void createAndShowGUI(final String path) {
        initImage(path);
        offset = (int) (imgWidth * 0.2f);
        frame = new JFrame(String.format("Image Stereogram - %s", path));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.add(new MyPanel());
        frame.pack();
        frame.setVisible(true);
        frame.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent evt) {
                formKeyPressed(evt);
            }
        });
        System.gc();
        Thread thread = new Thread(new Main());
        thread.start();
    }

    private void update() {

        System.arraycopy(originalPixels[frameNum], 0,
                outputPixels, 0, 3 * imgWidth * imgHeight);
        for (int y = 0; y < imgHeight; y++) {
            for (int x = 0; x < imgWidth - offset; x++) {

                for (int component = 0; component < 3; component++) {
                    int posOriginal = (x + y * imgWidth) * 3 + component;
                    outputPixels[posOriginal + offset * 3]
                            -= originalPixels[frameNum][posOriginal];
                    if (outputPixels[posOriginal + offset * 3] < 0) {
                        outputPixels[posOriginal + offset * 3] = 0;
                    }

                }
            }
        }

        outputRaster.setPixels(0, 0, imgWidth,
                imgHeight, outputPixels);
    }

    static private void initImage(final String path) {
        BufferedImage[] imgV = loadImg(path);

        imgWidth = -1;
        imgHeight = -1;
        for (Image img : imgV) {
            int frameWidth = img.getWidth(null);
            int frameHeight = img.getHeight(null);
            if (frameWidth > imgWidth) {
                imgWidth = frameWidth;
            }
            if (frameWidth > imgHeight) {
                imgHeight = frameHeight;
            }
        }

        BufferedImage originalImgV[] = new BufferedImage[imgV.length];
        outputImg = new BufferedImage(imgWidth, imgHeight,
                BufferedImage.TYPE_INT_RGB);
        outputRaster = outputImg.getRaster();
        outputPixels = outputRaster.getPixels(0, 0, imgWidth,
                imgHeight, outputPixels);
        for (int i = 0; i < originalImgV.length; i++) {
            originalImgV[i] = new BufferedImage(imgWidth, imgHeight,
                    BufferedImage.TYPE_INT_RGB);
            Graphics g = originalImgV[i].getGraphics();
            g.drawImage(imgV[i], 0, 0, null);

            WritableRaster originalRaster = originalImgV[i].getRaster();
            originalPixels[i] = originalRaster.getPixels(0, 0, imgWidth,
                    imgHeight, originalPixels[i]);
        }

    }

    static private BufferedImage[] loadImg(final String path) {
        try {
            ImageReader ir = ImageIO.getImageReadersBySuffix(
                    path.substring(path.lastIndexOf('.') + 1)).next();
            ImageInputStream imageInputStream
                    = ImageIO.createImageInputStream(new File(path));
            ir.setInput(imageInputStream);
            int numImages = ir.getNumImages(true);
            BufferedImage[] imgV = new BufferedImage[numImages];
            frameDelays = new int[numImages];
            originalPixels = new int[numImages][];
            for (int i = 0; i < numImages; i++) {
                IIOMetadata test = null;
                try {
                    test = ir.getImageMetadata(i);
                } catch (IOException e) {
                    System.out.println("Image doesn't seem to be "
                            + "an animated gif. (Couldn't get metadata)");
                }
                if (test != null && test instanceof GIFImageMetadata) {
                    frameDelays[i] = ((GIFImageMetadata) test).delayTime * 10;
                }
                if (frameDelays[i] < 1) {
                    frameDelays[i] = 1;
                }
                imgV[i] = ir.read(i);
            }
            imageInputStream.close();
            return imgV;
            
        } catch (IOException ex) {
            Logger.getLogger(Main.class
                    .getName()).log(Level.SEVERE, null, ex);
            return null;
        }

    }

    static private void formKeyPressed(java.awt.event.KeyEvent evt) {
        synchronized (offsetMutex) {
            if (evt.getKeyCode() == java.awt.event.KeyEvent.VK_LEFT) {
                offset--;
            } else if (evt.getKeyCode() == java.awt.event.KeyEvent.VK_RIGHT) {
                offset++;
            }
            if (offset < 0) {
                offset = 0;
            }
            if (offset > imgWidth) {
                offset = imgWidth;
            }
        }
    }

    @Override
    public void run() {
        long previousOffset = -1;
        long previousFrameNum = -1;
        while (true) {
            long oldTime = System.currentTimeMillis();
            synchronized (offsetMutex) {
                if (offset != previousOffset || frameNum != previousFrameNum) {
                    frame.setTitle(
                            String.format("ResX: %d Offset: %s "
                                    + "OffsetPercent: %.2f%%", 
                                    imgWidth, offset, 
                                    (float) offset / imgWidth * 100f));

                    previousOffset = offset;
                    previousFrameNum = frameNum;
                    update();
                    frame.repaint();
                }
            }
            long elapsedTime = System.currentTimeMillis() - oldTime;

            try {
                if (elapsedTime < targetTime) {
                    Thread.sleep(targetTime - elapsedTime);
                }
            } catch (InterruptedException ex) {
                Logger.getLogger(Main.class
                        .getName()).log(Level.SEVERE, null, ex);
            }
            frameDelayAccumulator += targetTime;
            while (frameDelayAccumulator >= frameDelays[frameNum]) {
                frameDelayAccumulator -= frameDelays[frameNum];
                frameNum = (frameNum + 1) % frameDelays.length;
            }
        }
    }

    private static class MyPanel extends JPanel {

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(imgWidth, imgHeight);
        }

        @Override
        public void paintComponent(Graphics g) {
            g.drawImage(outputImg, 0, 0, null);
        }
    }
}
