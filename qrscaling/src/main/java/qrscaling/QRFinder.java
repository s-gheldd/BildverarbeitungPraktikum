package qrscaling;

import ij.ImagePlus;
import ij.gui.Roi;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import sun.security.krb5.internal.PAData;
import util.Util;

import java.awt.*;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

public class QRFinder {

    private final static byte white = -1;
    private final static byte black = 0;
    private static final int STEP_COUNT = 3;
    public static final double FEATURE_SIZE_VARIANCE = 2.0d;

    private ByteProcessor binary;
    private int height;
    private int width;
    private Set<Pattern> patternSet = new HashSet<>();

    public void crop(ImageProcessor orig) {
        final Pattern[] patterns = patternSet.toArray(new Pattern[3]);

        final int first = getTopLeft(patterns);
        final Pattern topLeft = patterns[first];

        final int startx = (int) ((int) topLeft.column - 3.5 * topLeft.featureSize);
        final int starty = (int) ((int) topLeft.row - 3.5 * topLeft.featureSize);

        final int last = getBottomLeft(patterns);

        final Pattern bottomLeft = patterns[last];
        final int endY = (int) (bottomLeft.column + 7 * bottomLeft.featureSize);

        final int dist = endY - starty;

        orig.setRoi(startx, starty, dist, dist);
        final ImageProcessor crop = orig.crop();
        new ImagePlus("crop", crop).show();
    }

    private int getBottomLeft(final Pattern[] patterns) {

        int last = 0;
        int maxPos = getPos(patterns[0]);
        for (int i = 1; i < 3; i++) {
            final int pos = getPos(patterns[i]);
            if (pos < maxPos) {
                maxPos = pos;
                last = i;
            }
        }

        return last;
    }

    private int getPos(final Pattern pattern) {
        return pattern.row * width + pattern.column;
    }


    enum State {
        FIRST_BLACK, FIRST_WHITE, CENTRE_BLACK, SECOND_WHITE, SECOND_BLACK,
    }

    public boolean find(final ImageProcessor orig) {
        final byte[] pixels = prepareImage(orig);
        final LinkedList<PossiblePattern> possiblePatterns = getPossiblePatterns(pixels);

        for (final PossiblePattern possiblePattern : possiblePatterns) {
            final Pattern pattern = handlePossiblePattern(possiblePattern);
            if (pattern != null) {
                boolean add = true;
                for (Pattern existingPatterns : patternSet) {
                    if (existingPatterns.isInside(pattern)) {
                        add = false;
                    }
                }

                if (add) {
                    patternSet.add(pattern);
                }
            }
        }
        return patternSet.size() == 3;
    }

    public void draw(final ImageProcessor orig) {
        //System.err.println(patternSet.size());
        for (Pattern pattern : patternSet) {
            final int featureOffset = (int) (pattern.featureSize * 3.5d);

            orig.drawRect(pattern.column - featureOffset, pattern.row - featureOffset, 2 * featureOffset, 2 * featureOffset);
            orig.drawDot(pattern.column, pattern.row);
        }
    }

    public void rotate(final ImageProcessor orig) {
        if (patternSet.size() != 3) {
            throw new IllegalStateException("there must be exactly three found patterns.");
        }

        final Pattern[] patterns = patternSet.toArray(new Pattern[3]);

        final Pattern firstPattern = patterns[0];
        final Pattern secondPattern = patterns[1];
        final Pattern thirdPattern = patterns[2];


        final int oneToTwo = firstPattern.getDistanceSquared(secondPattern);
        final int oneToThree = firstPattern.getDistanceSquared(thirdPattern);
        final int twoToThree = secondPattern.getDistanceSquared(thirdPattern);


        final int threshold = (int) (((oneToTwo + oneToThree + twoToThree) / 3) * 0.2d);

        int first = -1;
        if (Math.abs(oneToTwo - oneToThree) < threshold) {
            first = 0;

            orig.drawString("0", firstPattern.column, firstPattern.row);

            final int dx = secondPattern.column - thirdPattern.column;
            final int dy = thirdPattern.row - secondPattern.row;

            final double tan = (double) dy / (double) dx;
            final double arc = Math.atan(tan) * Math.PI + 2;

            System.err.println(arc);
            orig.rotate(arc);

        } else if (Math.abs(oneToTwo - twoToThree) < threshold) {
            first = 1;
            orig.drawString("1", secondPattern.column, secondPattern.row);

            final int dx = firstPattern.column - thirdPattern.column;
            final int dy = thirdPattern.row - firstPattern.row;
            final double tan = (double) dy / (double) dx;
            final double arc = Math.atan(tan) * Math.PI + 2;

            System.err.println(arc);
            orig.rotate(arc);

        } else if (Math.abs(twoToThree - oneToThree) < threshold) {
            first = 2;
            orig.drawString("2", thirdPattern.column, thirdPattern.row);

            final int dx = secondPattern.column - firstPattern.column;
            final int dy = firstPattern.row - secondPattern.row;

            final double tan = (double) dy / (double) dx;
            final double arc = Math.atan(tan) * Math.PI + 2;

            System.err.println(arc);
            orig.rotate(arc);
        }

/*        final Pattern leftTop = patterns[first];





        int distance;
        if (leftTop == firstPattern) {
            distance = (int) Math.sqrt(oneToTwo);
        } else if (leftTop == secondPattern) {
            distance = (int) Math.sqrt(twoToThree);
        } else {
            distance = (int) Math.sqrt(oneToThree);
        }

        distance += 8 * leftTop.featureSize;

        orig.setRoi((int) (leftTop.column - 4 * leftTop.featureSize), (int) (leftTop.row - 4 * leftTop.featureSize), distance, distance);
        final ImageProcessor crop = orig.crop();
        new ImagePlus("crop", crop).show();*/

    }

    private int getTopLeft(final Pattern[] patterns) {
        final Pattern firstPattern = patterns[0];
        final Pattern secondPattern = patterns[1];
        final Pattern thirdPattern = patterns[2];


        final int oneToTwo = firstPattern.getDistanceSquared(secondPattern);
        final int oneToThree = firstPattern.getDistanceSquared(thirdPattern);
        final int twoToThree = secondPattern.getDistanceSquared(thirdPattern);


        final int threshold = (int) (((oneToTwo + oneToThree + twoToThree) / 3) * 0.2d);

        int first = -1;
        if (Math.abs(oneToTwo - oneToThree) < threshold) {
            first = 0;
        } else if (Math.abs(oneToTwo - twoToThree) < threshold) {
            first = 1;
        } else if (Math.abs(twoToThree - oneToThree) < threshold) {
            first = 2;
        }
        return first;
    }

    private Pattern handlePossiblePattern(PossiblePattern possiblePattern) {
        final int row = possiblePattern.row;
        final int col = possiblePattern.col;
        final int[] pixelCounts = possiblePattern.pixelCounts;
        final PatternCheckResult result = possiblePattern.result;
        final byte[] pixels = possiblePattern.pixels;

        int centreCol = centreFromEndOfPattern(pixelCounts, col);
        final int centreRow = crossCheck(row, centreCol, pixelCounts[2], result.totalPixelCount, pixels, width).centre;
        if (centreRow == -1) {
            return null;
        }
        final CrossCheckResult crossCheckResult = crossCheck(centreRow, centreCol, pixelCounts[2], result.totalPixelCount, pixels, 1);
        centreCol = crossCheckResult.centre;
        if (centreCol == -1) {
            return null;
        }
        centreCol = centreCol % width;
        final int diagonal = crossCheck(centreRow, centreCol, pixelCounts[2], result.totalPixelCount, pixels, width + 1).centre;
        if (diagonal == -1) {
            return null;
        }
        return new Pattern(centreRow, centreCol, crossCheckResult.result.featureSize);
    }

    byte[] prepareImage(ImageProcessor orig) {
        final ImageProcessor ip = orig.duplicate();
        final ByteProcessor gray;
        if (ip instanceof ColorProcessor) {
            gray = Util.makeGray(ip);
        } else {
            gray = (ByteProcessor) ip;
        }

        gray.autoThreshold();
        binary = gray;
        height = binary.getHeight();
        width = binary.getWidth();

        return (byte[]) binary.getPixels();
    }


    LinkedList<PossiblePattern> getPossiblePatterns(byte[] pixels) {
        final LinkedList<PossiblePattern> possiblePatterns = new LinkedList<>();

        for (int row = 0; row < height; row += STEP_COUNT) {
            State currentState = State.FIRST_BLACK;
            final int[] pixelCounts = new int[5];

            final int rowStart = row * width;
            for (int col = 0; col < width; col++) {
                final int pos = rowStart + col;
                final byte pixel = pixels[pos];
                // are we at a black pixel
                if (pixel == black) {
                    //state transition necessary?
                    if (currentState == State.FIRST_WHITE) {
                        currentState = State.CENTRE_BLACK;
                    } else if (currentState == State.SECOND_WHITE) {
                        currentState = State.SECOND_BLACK;
                    }
                    pixelCounts[currentState.ordinal()]++;
                    // or at a white
                } else if (pixel == white) {
                    if (currentState == State.SECOND_BLACK) {
                        //possibly done?
                        final PatternCheckResult result = checkPatternRatio(pixelCounts);
                        if (result.found) {
                            possiblePatterns.add(new PossiblePattern(pixels, result, pixelCounts.clone(), row, col));
                        }
                        // no finder pattern found we have to transition back to state SECOND_WHITE
                        pixelCounts[0] = pixelCounts[2];
                        pixelCounts[1] = pixelCounts[3];
                        pixelCounts[2] = pixelCounts[4];
                        pixelCounts[3] = 1;
                        pixelCounts[4] = 0;
                        currentState = State.SECOND_WHITE;
                    } else {
                        // normal state transition from B to W ?
                        if (currentState == State.FIRST_BLACK) {
                            currentState = State.FIRST_WHITE;
                        } else if (currentState == State.CENTRE_BLACK) {
                            currentState = State.SECOND_WHITE;
                        }
                        pixelCounts[currentState.ordinal()]++;

                    }
                } else {
                    throw new IllegalStateException("only binary images allowed at this state");
                }
            }
        }
        return possiblePatterns;
    }

    private static class Pattern {
        final int row;
        final int column;
        final int featureSize;

        private Pattern(int row, int column, int featureSize) {
            this.row = row;
            this.column = column;
            this.featureSize = featureSize;
        }

        public boolean isInside(final Pattern that) {
            final int distanceSquared = getDistanceSquared(that);
            return distanceSquared < (featureSize * 3.5) * (featureSize * 3.5);
        }

        public int getDistanceSquared(final Pattern that) {
            return (this.row - that.row) * (this.row - that.row) + (this.column - that.column) * (this.column - that.column);
        }
    }


    CrossCheckResult crossCheck(int centreRow, int centreCol, int centreCount, int totalPixelCount, byte[] pixels, int stepDistance) {
        final int startPos = centreRow * width + centreCol;
        final int[] checkPixelCounts = new int[5];

        int pos = startPos;
        while (pos >= 0 && pixels[pos] == black) {
            checkPixelCounts[State.CENTRE_BLACK.ordinal()]++;
            pos -= stepDistance;
        }
        if (pos < 0) {
            return CrossCheckResult.FAILED;
        }
        while (pos >= 0 && pixels[pos] == white) {
            checkPixelCounts[State.FIRST_WHITE.ordinal()]++;
            pos -= stepDistance;
        }
        if (pos < 0 || pixels[State.FIRST_WHITE.ordinal()] > centreCount) {
            // we must have whole of the FIRST_WHITE
            return CrossCheckResult.FAILED;
        }
        while (pos >= 0 && pixels[pos] == black) {
            checkPixelCounts[State.FIRST_BLACK.ordinal()]++;
            pos -= stepDistance;
        }
        if (pixels[State.FIRST_BLACK.ordinal()] > centreCount) {
            // if the image ends after the first black we can live with it
            return CrossCheckResult.FAILED;
        }

        // lets return to the middle
        pos = startPos + stepDistance;
        while (pos < pixels.length && pixels[pos] == black) {
            checkPixelCounts[State.CENTRE_BLACK.ordinal()]++;
            pos += stepDistance;
        }
        if (pos >= pixels.length) {
            return CrossCheckResult.FAILED;
        }
        while (pos < pixels.length && pixels[pos] == white) {
            checkPixelCounts[State.SECOND_WHITE.ordinal()]++;
            pos += stepDistance;
        }
        if (pos >= pixels.length || checkPixelCounts[State.SECOND_WHITE.ordinal()] > centreCount) {
            return CrossCheckResult.FAILED;
        }

        while (pos < pixels.length && pixels[pos] == black) {
            checkPixelCounts[State.SECOND_BLACK.ordinal()]++;
            pos += stepDistance;
        }
        if (checkPixelCounts[State.SECOND_BLACK.ordinal()] > centreCount) {
            return CrossCheckResult.FAILED;
        }

        final PatternCheckResult patternCheckResult = checkPatternRatio(checkPixelCounts);
        //some sanity checks
        if (5 * Math.abs(patternCheckResult.totalPixelCount - totalPixelCount) > 2 * totalPixelCount) {
            return CrossCheckResult.FAILED;
        }
        if (!patternCheckResult.found) {
            return CrossCheckResult.FAILED;
        }
        return new CrossCheckResult(centreFromEndOfPattern(checkPixelCounts, pos / stepDistance), patternCheckResult);
    }

    static class CrossCheckResult {
        private static CrossCheckResult FAILED = new CrossCheckResult(-1, PatternCheckResult.NO_RESULT);
        final int centre;
        final PatternCheckResult result;

        CrossCheckResult(final int centre, final PatternCheckResult result) {
            this.centre = centre;
            this.result = result;
        }
    }


    int centreFromEndOfPattern(final int[] pixelCounts, final int end) {
        return (int) ((end - pixelCounts[4] - pixelCounts[3]) - ((double) pixelCounts[2] / 2.0d));
    }

    private PatternCheckResult checkPatternRatio(final int[] pixelCounts) {

        int totalPixelCount = 0;
        for (int i = 0; i < pixelCounts.length; i++) {
            final int pixelCount = pixelCounts[i];
            totalPixelCount += pixelCount;
            if (pixelCount == 0) {
                // incomplete pattern
                return PatternCheckResult.NO_RESULT;
            }
        }
        if (totalPixelCount < 7) {
            //minimum for finder pattern is 7 pixels
            return PatternCheckResult.NO_RESULT;
        }

        final int featureSize = (int) Math.ceil((double) totalPixelCount / 7.0d);
        //maybe needs adjustment
        final int maxSigma = (int) ((double) featureSize / FEATURE_SIZE_VARIANCE);
        final boolean check = Math.abs(featureSize - pixelCounts[0]) <= maxSigma
                && Math.abs(featureSize - pixelCounts[1]) <= maxSigma
                && Math.abs(featureSize - pixelCounts[3]) <= maxSigma
                && Math.abs(featureSize - pixelCounts[4]) <= maxSigma
                && Math.abs(3 * featureSize - pixelCounts[2]) <= 3 * maxSigma;
        return new PatternCheckResult(check, totalPixelCount, featureSize);
    }


    static class PatternCheckResult {

        private final static PatternCheckResult NO_RESULT = new PatternCheckResult(false, -1, -1);

        final boolean found;
        final int totalPixelCount;
        final int featureSize;

        PatternCheckResult(boolean found, int totalPixelCount, int featureSize) {
            this.found = found;
            this.totalPixelCount = totalPixelCount;
            this.featureSize = featureSize;
        }
    }
}
