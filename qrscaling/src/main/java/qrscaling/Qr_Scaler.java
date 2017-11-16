package qrscaling;

import ij.ImagePlus;
import ij.plugin.filter.PlugInFilter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;

public class Qr_Scaler implements PlugInFilter {

    public static final byte floodFillMarker = (byte) 0xf0;
    private int numberOfSeams = 0;

    public int setup(final String arg, final ImagePlus imp) {
        numberOfSeams = Integer.parseInt(arg);
        return DOES_RGB | NO_UNDO | NO_CHANGES;
    }

    public void run(final ImageProcessor ip) {

        final QRFinder qrFinder = new QRFinder();

        final long start = System.currentTimeMillis();
        qrFinder.find(ip);
        System.out.println(System.currentTimeMillis() - start);
        qrFinder.draw(ip);

        new ImagePlus("edges", ip).show();
    }

    private ByteProcessor addWhiteBorder(final ByteProcessor gray) {
        final int width = gray.getWidth();
        final int height = gray.getHeight();
        final byte[] pixels = (byte[]) gray.getPixels();

        final int newWidth = width + 2;
        final int newHeight = height + 2;
        final byte[] newPixels = new byte[newHeight * newWidth];

        for (int row = 0; row < newHeight; row++) {
            final int rowStart = row * newWidth;
            for (int col = 0; col < newWidth; col++) {
                if (col == 0 || row == 0 || col == newWidth - 1 || row == newHeight - 1) {
                    newPixels[rowStart + col] = (byte) (255 & 0xff);
                } else {

                }
            }
        }
        return null;
    }

}
