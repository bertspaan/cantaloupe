package edu.illinois.library.cantaloupe.processor;

import edu.illinois.library.cantaloupe.image.Dimension;
import edu.illinois.library.cantaloupe.image.Info;
import edu.illinois.library.cantaloupe.image.Metadata;
import edu.illinois.library.cantaloupe.image.ScaleConstraint;
import edu.illinois.library.cantaloupe.operation.ColorTransform;
import edu.illinois.library.cantaloupe.operation.Crop;
import edu.illinois.library.cantaloupe.image.Format;
import edu.illinois.library.cantaloupe.operation.CropByPercent;
import edu.illinois.library.cantaloupe.operation.CropByPixels;
import edu.illinois.library.cantaloupe.operation.CropToSquare;
import edu.illinois.library.cantaloupe.operation.Encode;
import edu.illinois.library.cantaloupe.operation.OperationList;
import edu.illinois.library.cantaloupe.operation.Rotate;
import edu.illinois.library.cantaloupe.operation.Scale;
import edu.illinois.library.cantaloupe.operation.Transpose;
import edu.illinois.library.cantaloupe.source.PathStreamFactory;
import edu.illinois.library.cantaloupe.source.StreamFactory;
import edu.illinois.library.cantaloupe.test.BaseTest;
import edu.illinois.library.cantaloupe.test.TestUtil;
import org.junit.Ignore;
import org.junit.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static edu.illinois.library.cantaloupe.test.Assert.ImageAssert.*;
import static org.junit.Assert.*;

/**
 * <p>Contains tests common to all {@link Processor}s.</p>
 *
 * <p>The goal of this class is to test every {@link Processor} with every
 * {@link edu.illinois.library.cantaloupe.operation.Operation} on every
 * fixture image whose format it supports, with every output {@link Format}.</p>
 *
 * <p>Fixtures are obtained from {@link TestUtil#getImage(String)}. Fixture
 * names must start with the lowercased {@link Format#name()} and contain
 * {@literal WxHxS} (width, height, sample size) somewhere after that.</p>
 */
abstract class AbstractProcessorTest extends BaseTest {

    private static final double DELTA = 0.00000001;

    protected abstract Processor newInstance();

    private Processor newInstance(Path fixture, Format sourceFormat)
            throws UnsupportedSourceFormatException {
        Processor proc = newInstance();
        proc.setSourceFormat(sourceFormat);

        if (proc instanceof FileProcessor) {
            ((FileProcessor) proc).setSourceFile(fixture);
        } else if (proc instanceof StreamProcessor) {
            StreamFactory source = new PathStreamFactory(fixture);
            ((StreamProcessor) proc).setStreamFactory(source);
        }
        return proc;
    }

    Format getAnySupportedSourceFormat(Processor processor) {
        return getSupportedSourceFormats(processor).stream().findFirst().
                orElse(Format.UNKNOWN);
    }

    private Set<Format> getSupportedSourceFormats(Processor processor) {
        Set<Format> formats = EnumSet.noneOf(Format.class);
        for (Format format : Format.values()) {
            try {
                processor.setSourceFormat(format);
                formats.add(format);
            } catch (UnsupportedSourceFormatException e) {
                // continue
            }
        }
        return formats;
    }

    /* getSupportedIIIF11Qualities() */

    /**
     * Tests for the presence of all available IIIF 1.x qualities. Subclasses
     * must override if they lack support for any of these.
     */
    @Test
    public void testGetSupportedIIIF1Qualities() throws Exception {
        Processor proc = newInstance();
        proc.setSourceFormat(getAnySupportedSourceFormat(proc));

        Set<edu.illinois.library.cantaloupe.resource.iiif.v1.Quality> expectedQualities =
                EnumSet.of(
                        edu.illinois.library.cantaloupe.resource.iiif.v1.Quality.BITONAL,
                        edu.illinois.library.cantaloupe.resource.iiif.v1.Quality.COLOR,
                        edu.illinois.library.cantaloupe.resource.iiif.v1.Quality.GREY,
                        edu.illinois.library.cantaloupe.resource.iiif.v1.Quality.NATIVE);
        assertEquals(expectedQualities, proc.getSupportedIIIF1Qualities());
    }

    /* getSupportedIIIF20Qualities() */

    /**
     * Tests for the presence of all available IIIF 2.x qualities. Subclasses
     * must override if they lack support for any of these.
     */
    @Test
    public void testGetSupportedIIIF2Qualities() throws Exception {
        Processor proc = newInstance();
        proc.setSourceFormat(getAnySupportedSourceFormat(proc));

        Set<edu.illinois.library.cantaloupe.resource.iiif.v2.Quality> expectedQualities =
                EnumSet.of(
                        edu.illinois.library.cantaloupe.resource.iiif.v2.Quality.BITONAL,
                        edu.illinois.library.cantaloupe.resource.iiif.v2.Quality.COLOR,
                        edu.illinois.library.cantaloupe.resource.iiif.v2.Quality.GRAY,
                        edu.illinois.library.cantaloupe.resource.iiif.v2.Quality.DEFAULT);
        assertEquals(expectedQualities, proc.getSupportedIIIF2Qualities());
    }

    /* process() */

    @Test
    public void testProcessWithNoOperations() throws Exception {
        OperationList ops = new OperationList(new Encode(Format.JPG)); // OK, one operation, but it's required

        forEachFixture(ops, new ProcessorAssertion() {
            @Override
            public void run() {
                if (this.sourceSize != null) {
                    assertEquals(this.sourceSize.width(),
                            this.resultingImage.getWidth(), DELTA);
                    assertEquals(this.sourceSize.height(),
                            this.resultingImage.getHeight(), DELTA);
                }
            }
        });
    }

    @Test
    public void testProcessWithOnlyNoOpOperations() throws Exception {
        Scale scale = new Scale();
        Rotate rotate = new Rotate(0);
        OperationList ops = new OperationList(
                scale, rotate, new Encode(Format.JPG));

        forEachFixture(ops, new ProcessorAssertion() {
            @Override
            public void run() {
                if (this.sourceSize != null) {
                    assertEquals(this.sourceSize.width(),
                            this.resultingImage.getWidth(), DELTA);
                    assertEquals(this.sourceSize.height(),
                            this.resultingImage.getHeight(), DELTA);
                }
            }
        });
    }

    @Test
    public void testProcessWithScaleConstraint() throws Exception {
        OperationList ops = new OperationList(
                new Scale(),
                new Encode(Format.JPG));
        ops.setScaleConstraint(new ScaleConstraint(1, 2));

        forEachFixture(ops, new ProcessorAssertion() {
            @Override
            public void run() {
                if (this.sourceSize != null) {
                    assertEquals(this.sourceSize.width() / 2.0,
                            this.resultingImage.getWidth(), DELTA);
                    assertEquals(this.sourceSize.height() / 2.0,
                            this.resultingImage.getHeight(), DELTA);
                }
            }
        });
    }

    @Test
    public void testProcessWithSquareCropOperation() throws Exception {
        CropToSquare crop = new CropToSquare();
        OperationList ops = new OperationList(crop, new Encode(Format.JPG));

        forEachFixture(ops, new ProcessorAssertion() {
            @Override
            public void run() {
                if (this.sourceSize != null) {
                    double expectedSize = (this.sourceSize.width() > this.sourceSize.height()) ?
                            this.sourceSize.height() : this.sourceSize.width();
                    assertEquals(expectedSize,
                            this.resultingImage.getWidth(), DELTA);
                    assertEquals(expectedSize,
                            this.resultingImage.getHeight(), DELTA);
                }
            }
        });
    }

    @Test
    public void testProcessWithCropByPixelsOperation() throws Exception {
        OperationList ops = new OperationList(
                new CropByPixels(10, 10, 35, 30),
                new Encode(Format.JPG));

        boolean tmpFlag = false;
        try (Processor proc = newInstance()) {
            if (proc instanceof AbstractImageIOProcessor) {
                tmpFlag = true;
            }
        }
        final boolean flag = tmpFlag;

        forEachFixture(ops, new ProcessorAssertion() {
            {
                if (flag) {
                    // Rotated images won't work due to the way the Crop operation
                    // is set up.
                    this.skippedFixtures.add("jpg-exif-orientation-270.jpg");
                    this.skippedFixtures.add("jpg-xmp-orientation-90.jpg");
                    this.skippedFixtures.add("png-rotated.png");
                    this.skippedFixtures.add("tif-rotated.tif");
                }
            }
            @Override
            public void run() {
                assertEquals(35, this.resultingImage.getWidth());
                assertEquals(30, this.resultingImage.getHeight());
            }
        });
    }

    @Test
    public void testProcessWithCropByPercentOperation() throws Exception {
        final double width = 0.2;
        final double height = 0.2;
        Crop crop = new CropByPercent(0.2, 0.2, width, height);
        OperationList ops = new OperationList(crop, new Encode(Format.JPG));

        forEachFixture(ops, new ProcessorAssertion() {
            @Override
            public void run() {
                if (this.sourceSize != null) {
                    // Be a little lenient.
                    long expectedW = Math.round(this.sourceSize.width() * width);
                    long expectedH = Math.round(this.sourceSize.height() * height);
                    assertTrue(Math.abs(expectedW - this.resultingImage.getWidth()) < 2);
                    assertTrue(Math.abs(expectedH - this.resultingImage.getHeight()) < 2);
                }
            }
        });
    }

    @Test
    public void testProcessWithNullScaleOperation() throws Exception {
        OperationList ops = new OperationList(
                new Scale(), new Encode(Format.JPG));

        forEachFixture(ops, new ProcessorAssertion() {
            @Override
            public void run() {
                if (this.sourceSize != null) {
                    assertEquals(this.sourceSize.width(),
                            this.resultingImage.getWidth(), DELTA);
                    assertEquals(this.sourceSize.height(),
                            this.resultingImage.getHeight(), DELTA);
                }
            }
        });
    }

    @Test
    public void testProcessWithScaleAspectFitWidthOperation() throws Exception {
        OperationList ops = new OperationList(
                new Scale(20, null, Scale.Mode.ASPECT_FIT_WIDTH),
                new Encode(Format.JPG));

        forEachFixture(ops, new ProcessorAssertion() {
            @Override
            public void run() {
                if (this.sourceSize != null) {
                    double expectedHeight = 20 /
                            this.sourceSize.width() * this.sourceSize.height();
                    assertEquals(20, this.resultingImage.getWidth());
                    assertTrue(Math.abs(expectedHeight - this.resultingImage.getHeight()) < 1);
                }
            }
        });
    }

    @Test
    public void testProcessWithScaleAspectFitHeightOperation() throws Exception {
        OperationList ops = new OperationList(
                new Scale(null, 20, Scale.Mode.ASPECT_FIT_HEIGHT),
                new Encode(Format.JPG));

        forEachFixture(ops, new ProcessorAssertion() {
            @Override
            public void run() {
                if (this.sourceSize != null) {
                    double expectedWidth = 20 /
                            this.sourceSize.height() * this.sourceSize.width();
                    assertTrue(Math.abs(expectedWidth - this.resultingImage.getWidth()) < 1);
                    assertEquals(20, this.resultingImage.getHeight());
                }
            }
        });
    }

    @Test
    public void testProcessWithDownscaleByPercentageOperation() throws Exception {
        OperationList ops = new OperationList(
                new Scale(0.5),
                new Encode(Format.JPG));

        forEachFixture(ops, new ProcessorAssertion() {
            @Override
            public void run() {
                if (this.sourceSize != null) {
                    assertEquals(this.sourceSize.width() * 0.5,
                            this.resultingImage.getWidth(), DELTA);
                    assertEquals(this.sourceSize.height() * 0.5,
                            this.resultingImage.getHeight(), DELTA);
                }
            }
        });
    }

    @Test
    public void testProcessWithUpscaleByPercentageOperation() throws Exception {
        OperationList ops = new OperationList(
                new Scale(1.5),
                new Encode(Format.JPG));

        forEachFixture(ops, new ProcessorAssertion() {
            @Override
            public void run() {
                if (this.sourceSize != null) {
                    assertEquals(this.sourceSize.width() * 1.5,
                            this.resultingImage.getWidth(), DELTA);
                    assertEquals(this.sourceSize.height() * 1.5,
                            this.resultingImage.getHeight(), DELTA);
                }
            }
        });
    }

    @Test
    public void testProcessWithAspectFitInsideScaleOperation() throws Exception {
        OperationList ops = new OperationList(
                new Scale(20, 20, Scale.Mode.ASPECT_FIT_INSIDE),
                new Encode(Format.JPG));

        forEachFixture(ops, new ProcessorAssertion() {
            @Override
            public void run() {
                if (this.sourceSize != null) {
                    double expectedW = 20, expectedH = 20;
                    if (this.sourceSize.width() > this.sourceSize.height()) {
                        expectedH = (this.sourceSize.height() / this.sourceSize.width()) * 20;
                    } else if (this.sourceSize.width() < this.sourceSize.height()) {
                        expectedW = (this.sourceSize.width() / this.sourceSize.height()) * 20;
                    }
                    assertTrue(Math.abs(expectedW - this.resultingImage.getWidth()) < 1);
                    assertTrue(Math.abs(expectedH - this.resultingImage.getHeight()) < 1);
                }
            }
        });
    }

    @Test
    public void testProcessWithNonAspectFillScaleOperation() throws Exception {
        OperationList ops = new OperationList(
                new Scale(20, 20, Scale.Mode.NON_ASPECT_FILL),
                new Encode(Format.JPG));

        forEachFixture(ops, new ProcessorAssertion() {
            @Override
            public void run() {
                assertEquals(20, this.resultingImage.getWidth());
                assertEquals(20, this.resultingImage.getHeight());
            }
        });
    }

    @Test
    public void testProcessWithTransposeOperation() throws Exception {
        OperationList ops = new OperationList(
                Transpose.HORIZONTAL,
                new Encode(Format.JPG));

        forEachFixture(ops, new ProcessorAssertion() {
            @Override
            public void run() {
                if (this.sourceSize != null) {
                    assertEquals(this.sourceSize.width(),
                            this.resultingImage.getWidth(), DELTA);
                    assertEquals(this.sourceSize.height(),
                            this.resultingImage.getHeight(), DELTA);
                }
            }
        });
    }

    @Test
    public void testProcessWithRotate0DegreesOperation() throws Exception {
        OperationList ops = new OperationList(
                new Rotate(0), new Encode(Format.JPG));

        forEachFixture(ops, new ProcessorAssertion() {
            @Override
            public void run() {
                if (this.sourceSize != null) {
                    assertEquals(this.sourceSize.width(),
                            this.resultingImage.getWidth(), DELTA);
                    assertEquals(this.sourceSize.height(),
                            this.resultingImage.getHeight(), DELTA);
                }
            }
        });
    }

    @Test
    public void testProcessWithRotate275DegreesOperation() throws Exception {
        OperationList ops = new OperationList(
                new Rotate(275), new Encode(Format.JPG));

        forEachFixture(ops, new ProcessorAssertion() {
            @Override
            public void run() {
                if (this.sourceSize != null) {
                    final double radians = Math.toRadians(275);
                    double expectedW = Math.abs(this.sourceSize.width() * Math.cos(radians)) +
                            Math.abs(this.sourceSize.height() * Math.sin(radians));
                    double expectedH = Math.abs(this.sourceSize.width() * Math.sin(radians)) +
                            Math.abs(this.sourceSize.height() * Math.cos(radians));
                    expectedW = Math.round(expectedW);
                    expectedH = Math.round(expectedH);

                    // Be a little lenient. Different processors will use
                    // different antialiasing methods and have different ideas
                    // on how much to pad the edges.
                    assertTrue(Math.abs(expectedW - this.resultingImage.getWidth()) < 4);
                    assertTrue(Math.abs(expectedH - this.resultingImage.getHeight()) < 4);
                }
            }
        });
    }

    @Test
    public void testProcessWithBitonalFilterOperation() throws Exception {
        OperationList ops = new OperationList(
                ColorTransform.BITONAL,
                new Encode(Format.PNG));

        forEachFixture(ops, new ProcessorAssertion() {
            {
                if (newInstance() instanceof JaiProcessor) {
                    // These may be JAI bugs
                    this.skippedFixtures.add("bmp-rgba-64x56x8.bmp");
                    this.skippedFixtures.add("gif");
                    this.skippedFixtures.add("gif-animated-looping.gif");
                    this.skippedFixtures.add("gif-animated-non-looping.gif");
                    this.skippedFixtures.add("gif-rgb-64x56x8.gif");
                    this.skippedFixtures.add("gif-xmp.gif");
                    this.skippedFixtures.add("gif-xmp-orientation-90.gif");
                }
            }
            @Override
            public void run() {
                assertBitonal(this.resultingImage);
            }
        });
    }

    @Test
    public void testProcessWithGrayscaleFilterOperation() throws Exception {
        OperationList ops = new OperationList(
                ColorTransform.GRAY,
                new Encode(Format.JPG));

        forEachFixture(ops, new ProcessorAssertion() {
            {
                if (newInstance() instanceof JaiProcessor) {
                    // These may be JAI bugs
                    this.skippedFixtures.add("bmp-rgba-64x56x8.bmp");
                    this.skippedFixtures.add("gif");
                    this.skippedFixtures.add("gif-animated-looping.gif");
                    this.skippedFixtures.add("gif-animated-non-looping.gif");
                    this.skippedFixtures.add("gif-rgb-64x56x8.gif");
                    this.skippedFixtures.add("gif-xmp.gif");
                    this.skippedFixtures.add("gif-xmp-orientation-90.gif");
                }
            }
            @Override
            public void run() {
                assertGray(this.resultingImage);
            }
        });
    }

    @Test
    public void testProcessWithAllSupportedOutputFormats() throws Exception {
        boolean tmpFlag = false;
        Set<Format> outputFormats;
        try (Processor proc = newInstance()) {
            proc.setSourceFormat(getAnySupportedSourceFormat(proc));
            outputFormats = proc.getAvailableOutputFormats();
            if (proc instanceof Java2dProcessor || proc instanceof JaiProcessor) {
                tmpFlag = true;
            }
        }
        final boolean flag = tmpFlag;

        for (Format outputFormat : outputFormats) {
            OperationList ops = new OperationList(new Encode(outputFormat));

            forEachFixture(ops, new ProcessorAssertion() {
                {
                    if (flag) {
                        this.skippedFixtures.add("bmp-rgba-64x56x8.bmp");
                    }
                }
                @Override
                public void run() {
                    if (this.sourceSize != null) {
                        assertEquals(this.sourceSize.width(),
                                this.resultingImage.getWidth(), DELTA);
                    }
                }
            });
        }
    }

    @Test
    @Ignore // see comment in GIFImageWriter, which most processors use to write GIFs
    public void testProcessWritesXMPMetadataIntoGIF() throws Exception {
        testProcessWritesXMPMetadata(Format.GIF);
    }

    @Test
    public void testProcessWritesXMPMetadataIntoJPEG() throws Exception {
        testProcessWritesXMPMetadata(Format.JPG);
    }

    @Test
    public void testProcessWritesXMPMetadataIntoPNG() throws Exception {
        testProcessWritesXMPMetadata(Format.PNG);
    }

    @Test
    public void testProcessWritesXMPMetadataIntoTIFF() throws Exception {
        testProcessWritesXMPMetadata(Format.TIF);
    }

    private void testProcessWritesXMPMetadata(Format outputFormat)
            throws Exception {
        final String xmp = "<rdf:RDF>this is some fake XMP</rdf:RDF>";
        final Metadata metadata = new Metadata();
        metadata.setXMP(xmp);

        final Encode encode = new Encode(outputFormat);
        encode.setMetadata(metadata);
        final OperationList ops = new OperationList(encode);

        forEachFixture(ops, new ProcessorAssertion() {
            @Override
            public void run() {
                String imageStr = new String(
                        this.resultingRawImage, StandardCharsets.US_ASCII);
                assertTrue(imageStr.contains(xmp));
            }
        });
    }

    /* readInfo() */

    /**
     * This implementation is tile-unaware. Tile-aware processors will need to
     * override it.
     */
    @Test
    public void testReadInfoOnAllFixtures() throws Exception {
        for (Format format : Format.values()) {
            for (Path fixture : TestUtil.getImageFixtures(format)) {
                if (fixture.getFileName().toString().equals("jp2") ||
                        fixture.getFileName().toString().equals("jp2-iptc.jp2")) {
                    continue;
                }
                try (final Processor proc = newInstance()) {
                    proc.setSourceFormat(format);

                    if (proc instanceof StreamProcessor) {
                        StreamProcessor sproc = (StreamProcessor) proc;
                        StreamFactory streamFactory =
                                new PathStreamFactory(fixture);
                        sproc.setStreamFactory(streamFactory);
                    } else if (proc instanceof FileProcessor) {
                        FileProcessor fproc = (FileProcessor) proc;
                        fproc.setSourceFile(fixture);
                    }

                    // We don't know the dimensions of the source image and
                    // we can't get them because that would require using
                    // the method we are now testing, so the best we can do
                    // is to assert that they are nonzero.
                    final Info actualInfo = proc.readInfo();
                    assertEquals(format, actualInfo.getSourceFormat());
                    assertTrue(actualInfo.getSize().width() >= 1);
                    assertTrue(actualInfo.getSize().height() >= 1);

                    // Parse the resolution count from the filename, or
                    // else assert 1.
                    int expectedNumResolutions = 1;
                    Pattern pattern = Pattern.compile("\\dres");
                    Matcher matcher = pattern.matcher(fixture.getFileName().toString());
                    if (matcher.find()) {
                        expectedNumResolutions =
                                Integer.parseInt(matcher.group(0).substring(0, 1));
                    }
                    assertEquals(expectedNumResolutions,
                            actualInfo.getNumResolutions());
                } catch (UnsupportedSourceFormatException ignore) {
                    // The processor doesn't support this format, which is
                    // fine. No processor supports all formats.
                } catch (Exception e) {
                    System.err.println(format + " : " + fixture);
                    throw e;
                }
            }
        }
    }

    /* setSourceFormat() */

    @Test
    public void testSetSourceFormatWithUnsupportedSourceFormat() {
        for (Format format : Format.values()) {
            try {
                final Processor proc = newInstance();
                proc.setSourceFormat(format);
                if (proc.getAvailableOutputFormats().isEmpty()) {
                    fail("Expected exception");
                }
            } catch (UnsupportedSourceFormatException e) {
                // pass
            }
        }
    }

    /**
     * Tests {@link Processor#process} with every one of the fixtures for every
     * source format the processor supports.
     */
    void forEachFixture(final OperationList ops,
                        final ProcessorAssertion assertion) throws Exception {
        Files.walkFileTree(TestUtil.getFixture("images"), new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file,
                                             BasicFileAttributes attrs) throws IOException {
                if (assertion.skippedFixtures.contains(file.getFileName().toString())) {
                    return FileVisitResult.CONTINUE;
                }

                Set<Format> supportedFormats =
                        getSupportedSourceFormats(newInstance());
                for (Format sourceFormat : supportedFormats) {
                    final String fixtureName = file.getFileName().toString();

                    Processor proc = newInstance();

                    // These are used in other tests, but ImageIO doesn't like
                    // them.
                    if (Set.of("jpg-ycck.jpg", "jpg-icc-chunked.jpg").contains(fixtureName)) {
                        continue;
                    }

                    // TODO: address these
                    if (proc instanceof Java2dProcessor || proc instanceof JaiProcessor) {
                        if (fixtureName.equals("tif-rgba-1res-64x56x8-tiled-jpeg.tif") ||
                                fixtureName.equals("tif-rgba-1res-64x56x8-striped-jpeg.tif")) {
                            continue;
                        }
                    } else if (proc instanceof GraphicsMagickProcessor) {
                        if (fixtureName.equals("jpg-rgb-594x522x8-baseline.jpg")) {
                            continue;
                        }
                    } else if (proc instanceof ImageMagickProcessor) {
                        if (fixtureName.contains("pdf")) {
                            continue;
                        }
                    }

                    // Don't test 1x1 images as they are problematic with
                    // cropping & scaling.
                    if (fixtureName.startsWith(sourceFormat.name().toLowerCase()) &&
                            !fixtureName.contains("-1x1")) {
                        // Extract the dimensions and sample size from the
                        // fixture name to use in the assertion.
                        Pattern pattern = Pattern.compile("\\d+x\\d+x\\d+");
                        Matcher matcher = pattern.matcher(fixtureName);
                        if (matcher.find()) {
                            String match = matcher.group();
                            String[] parts = match.split("x");
                            if (parts.length > 1) {
                                assertion.sourceSize = new Dimension(
                                        Integer.parseInt(parts[0]),
                                        Integer.parseInt(parts[1]));
                            }
                            if (parts.length > 2) {
                                assertion.sourceSampleSize =
                                        Integer.parseInt(parts[2]);
                            }
                        } else {
                            assertion.sourceSize = null;
                            assertion.sourceSampleSize = 0;
                        }

                        try {
                            doProcessTest(file, sourceFormat, ops, assertion);
                        } catch (Exception | AssertionError e) {
                            System.err.println("FAILED: " + file);
                            throw new IOException(e.getMessage(), e);
                        }
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Instantiates a processor, configures it with the given arguments, and
     * runs the given assertion.
     */
    private void doProcessTest(final Path fixture,
                               final Format sourceFormat,
                               final OperationList opList,
                               final ProcessorAssertion assertion) throws Exception {
        try (Processor proc = newInstance(fixture, sourceFormat)) {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            proc.process(opList, proc.readInfo(), os);
            final byte[] imageBytes = os.toByteArray();

            ByteArrayInputStream is = new ByteArrayInputStream(imageBytes);
            BufferedImage image = ImageIO.read(is);

            assertion.opList            = opList;
            assertion.resultingImage    = image;
            assertion.resultingRawImage = imageBytes;
            assertion.run();
        } catch (Exception | AssertionError e) {
            System.err.println("Errored fixture: " + fixture);
            System.err.println("Errored op list: " + opList);
            throw e;
        }
    }

}
