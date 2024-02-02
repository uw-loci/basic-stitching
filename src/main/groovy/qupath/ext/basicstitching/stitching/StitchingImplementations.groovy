package qupath.ext.basicstitching.stitching

import org.slf4j.LoggerFactory
import qupath.ext.basicstitching.utilities.UtilityFunctions
import qupath.lib.common.GeneralTools
import qupath.lib.gui.QuPathGUI
import qupath.lib.gui.dialogs.Dialogs
import qupath.lib.images.servers.ImageServerProvider
import qupath.lib.images.servers.ImageServers
import qupath.lib.images.servers.SparseImageServer
import qupath.lib.images.writers.ome.OMEPyramidWriter
import qupath.lib.regions.ImageRegion

import javax.imageio.ImageIO
import javax.imageio.plugins.tiff.BaselineTIFFTagSet
import javax.imageio.plugins.tiff.TIFFDirectory
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import static qupath.lib.scripting.QP.getLogger;

//TODO Is there a way to apply the downsample when accessing the image regions so the full res image region doesn't need to be stored in memory?
// Maybe downsample after acquiring the region as a second step?

// Interface for stitching strategies
interface StitchingStrategy {
    List<Map> prepareStitching(String folderPath, double pixelSizeInMicrons, double baseDownsample, String matchingString)
}

//TODO Look into reducing repetitive code here, though maybe the overall structure is wrong?
//abstract class BaseStitchingStrategy implements StitchingStrategy {
//    protected List<Map> prepareStitchingCommon(String folderPath, double pixelSizeInMicrons, double baseDownsample, String matchingString, Closure<Map> processSubDirectory) {
//        def logger = LoggerFactory.getLogger(QuPathGUI.class)
//        Path rootdir = Paths.get(folderPath)
//        List<Map> allFileRegionMaps = []
//
//        Files.newDirectoryStream(rootdir).each { path ->
//            if (Files.isDirectory(path) && path.fileName.toString().contains(matchingString)) {
//                def fileRegionMaps = processSubDirectory.call(path)
//                allFileRegionMaps += fileRegionMaps
//            }
//        }
//
//        if (allFileRegionMaps.isEmpty()) {
//            Dialogs.showWarningNotification("Warning", "No valid tile configurations found in any subdirectory.")
//            return []
//        }
//
//        allFileRegionMaps
//    }
//}
// Concrete strategy for stitching based on file names

/**
 * Strategy for stitching images based on file names.
 * This class implements the {@link StitchingStrategy} interface and provides a specific
 * algorithm for stitching based on the naming convention of image files.
 */
class FileNameStitchingStrategy implements StitchingStrategy {

    /**
     * Prepares stitching by processing each subdirectory within the specified root directory.
     * This method iterates over each subdirectory that matches the given criteria and
     * aggregates file-region mapping information for stitching.
     *
     * @param folderPath The path to the root directory containing image files.
     * @param pixelSizeInMicrons The pixel size in microns, used for calculating image regions.
     * @param baseDownsample The base downsample value for the stitching process.
     * @param matchingString A string to match for selecting relevant subdirectories.
     * @return A list of maps, each map containing file, region, and subdirName information for stitching.
     */
    @Override
    List<Map> prepareStitching(String folderPath, double pixelSizeInMicrons, double baseDownsample, String matchingString) {
        Path rootdir = Paths.get(folderPath)
        List<Map> allFileRegionMaps = []

        // Iterate over each subdirectory in the root directory
        Files.newDirectoryStream(rootdir).each { path ->
            if (Files.isDirectory(path) && path.fileName.toString().contains(matchingString)) {
                // Process each subdirectory and collect file-region mappings
                def fileRegionMaps = processSubDirectory(path, pixelSizeInMicrons, baseDownsample)
                allFileRegionMaps += fileRegionMaps
            }
        }

        // Check if any valid file-region mappings were found
        if (allFileRegionMaps.isEmpty()) {
            Dialogs.showWarningNotification("Warning", "No valid tile configurations found in any subdirectory.")
            return
        }

        allFileRegionMaps
    }

    /**
     * Processes a single subdirectory to generate file-region mappings for stitching.
     * This method collects all TIFF files in the directory, builds tile configurations,
     * and creates a mapping of each file to its corresponding image region.
     *
     * @param dir The path to the subdirectory to be processed.
     * @param pixelSizeInMicrons The pixel size in microns for calculating image regions.
     * @param baseDownsample The base downsample value for the stitching process.
     * @return A list of maps, each map containing file, region, and subdirName for stitching.
     */
    private static List<Map> processSubDirectory(Path dir, double pixelSizeInMicrons, double baseDownsample) {
        def logger = LoggerFactory.getLogger(QuPathGUI.class)
        logger.info("Processing slide in folder $dir")

        // Collect all TIFF files in the directory
        def files = []
        Files.newDirectoryStream(dir, "*.tif*").each { path ->
            files << path.toFile()
        }

        // Build tile configurations from the collected files
        def tileConfigOutput = buildTileConfigWithMinCoordinates(dir)
        def tileConfig = tileConfigOutput[0]
        def minimumXY = tileConfigOutput[1]

        // Create file-region mappings for each file
        List<Map> fileRegionMaps = []
        files.each { file ->
            def region = parseRegionFromOffsetTileConfig(file as File, tileConfig as List<Map>, minimumXY, pixelSizeInMicrons)
            if (region) {
                // Add file, region, and subdir name to the mappings
                fileRegionMaps << [file: file, region: region, subdirName: dir.getFileName().toString()]
            }
        }

        return fileRegionMaps
    }

/**
 * Parses an image region from offset tile configuration.
 * This method calculates the region of an image file based on its position within a larger stitched image.
 *
 * @param file The image file for which to parse the region.
 * @param tileConfig A list of maps containing tile configurations, each with image name and its coordinates.
 * @param minimumXY The minimum x and y coordinates among all tiles, used for offset calculations.
 * @param pixelSizeInMicrons The size of a pixel in microns, used for scaling coordinates.
 * @param z The z-plane index of the image (default is 0).
 * @param t The timepoint index of the image (default is 0).
 * @return An ImageRegion object representing the specified region of the image or null if not found.
 */
    static ImageRegion parseRegionFromOffsetTileConfig(File file, List<Map> tileConfig, minimumXY, double pixelSizeInMicrons, int z = 0, int t = 0) {
        String imageName = file.getName()
        def config = tileConfig.find { it.imageName == imageName }
        def logger = LoggerFactory.getLogger(QuPathGUI.class)

        if (config) {
            int x = (config.x - minimumXY[0]) / pixelSizeInMicrons as int
            int y = (config.y - minimumXY[1]) / pixelSizeInMicrons as int
            def dimensions = UtilityFunctions.getTiffDimensions(file)
            if (dimensions == null) {
                logger.info("Could not retrieve dimensions for image $imageName")
                return null
            }
            int width = dimensions.width
            int height = dimensions.height
            return ImageRegion.createInstance(x, y, width, height, z, t)
        } else {
            logger.info("No configuration found for image $imageName")
            return null
        }
    }

/**
 * Builds tile configurations with minimum coordinates for a given directory.
 * This method scans a directory for image files and extracts their coordinates from the file names.
 * It then calculates the minimum X and Y coordinates among all images.
 *
 * @param dir The directory path containing the image files.
 * @return A list containing two elements: a list of image configurations and an array [minX, minY].
 */
    static def buildTileConfigWithMinCoordinates(Path dir) {
        def images = []
        def logger = LoggerFactory.getLogger(QuPathGUI.class)
        Files.newDirectoryStream(dir, "*.{tif,tiff,ome.tif}").each { path ->
            def matcher = path.fileName.toString() =~ /.*\[(\d+),(\d+)\].*\.(tif|tiff|ome.tif)$/
            if (matcher.matches()) {
                def imageName = path.getFileName().toString()
                int x = Integer.parseInt(matcher[0][1])
                int y = Integer.parseInt(matcher[0][2])
                images << ['imageName': imageName, 'x': x, 'y': y]
            }
        }

        def minX = images.min { it.x }?.x ?: 0
        def minY = images.min { it.y }?.y ?: 0
        return [images, [minX, minY]]
    }
}


/**
 * Strategy for stitching images based on tile configurations specified in a TileConfiguration.txt file.
 * This class implements the {@link StitchingStrategy} interface, providing an algorithm
 * for processing and stitching images based on their defined tile configurations.
 */
class TileConfigurationTxtStrategy implements StitchingStrategy {

    /**
     * Prepares stitching by processing each subdirectory within the specified root directory.
     * This method iterates over each subdirectory that matches the given criteria and
     * aggregates file-region mapping information for stitching.
     *
     * @param folderPath The path to the root directory containing image files.
     * @param pixelSizeInMicrons The pixel size in microns, used for calculating image regions.
     * @param baseDownsample The base downsample value for the stitching process.
     * @param matchingString A string to match for selecting relevant subdirectories.
     * @return A list of maps, each map containing file, region, and subdirName information for stitching.
     */
    @Override
    List<Map> prepareStitching(String folderPath, double pixelSizeInMicrons, double baseDownsample, String matchingString) {
        Path rootdir = Paths.get(folderPath)
        List<Map> allFileRegionMaps = []

        // Iterate over each subdirectory in the root directory
        Files.newDirectoryStream(rootdir).each { path ->
            if (Files.isDirectory(path) && path.fileName.toString().contains(matchingString)) {
                // Process each subdirectory and collect file-region mappings
                def fileRegionMaps = processSubDirectory(path, pixelSizeInMicrons, baseDownsample)
                allFileRegionMaps += fileRegionMaps
            }
        }

        // Check if any valid file-region mappings were found
        if (allFileRegionMaps.isEmpty()) {
            Dialogs.showWarningNotification("Warning", "No valid tile configurations found in any subdirectory.")
            return []
        }

        allFileRegionMaps
    }

    /**
     * Processes a single subdirectory to generate file-region mappings for stitching.
     * This method reads the TileConfiguration.txt file in the directory (if present),
     * collects all TIFF files, and creates a mapping of each file to its corresponding image region.
     *
     * @param dir The path to the subdirectory to be processed.
     * @param pixelSizeInMicrons The pixel size in microns for calculating image regions.
     * @param baseDownsample The base downsample value for the stitching process.
     * @return A list of maps, each map containing file, region, and subdirName for stitching.
     */
    private static List<Map> processSubDirectory(Path dir, double pixelSizeInMicrons, double baseDownsample) {
        def logger = LoggerFactory.getLogger(QuPathGUI.class)
        logger.info("Processing slide in folder $dir")

        // Check for the existence of TileConfiguration.txt in the directory
        Path tileConfigPath = dir.resolve("TileConfiguration.txt")
        if (!Files.exists(tileConfigPath)) {
            logger.info("Skipping folder as TileConfiguration.txt is missing: $dir")
            return []
        }
        def tileConfig = parseTileConfiguration(tileConfigPath.toString())

        logger.info('completed parseTileConfiguration')

        // Collect all TIFF files in the directory
        List<File> files = []
        Files.newDirectoryStream(dir, "*.tif*").each { path ->
            files << path.toFile()
        }
        // Extract file names from tileConfig
        Set<String> tileConfigFileNames = tileConfig.collect { it.imageName }

        // Extract file names from the directory
        Set<String> directoryFileNames = files.collect { it.name }

        // Check if tileConfig file names match with the actual file names in the directory
        if (!tileConfigFileNames.equals(directoryFileNames)) {
            logger.warn("Mismatch between tile configuration file names and actual file names in directory: $dir")

            return [] // Optionally skip processing if there is a mismatch
        }
        // Create file-region mappings for each file
        List<Map> fileRegionMaps = []
        files.each { File file ->
            logger.info("parsing region from file $file")
            ImageRegion region = parseRegionFromTileConfig(file as File, tileConfig as List<Map>)
            if (region) {
                logger.info("Processing file: ${file.path}")
                fileRegionMaps << [file: file, region: region, subdirName: dir.getFileName().toString()]
            }
        }

        return fileRegionMaps
    }
    /**
     * Parses the 'TileConfiguration.txt' file to extract image names and their coordinates.
     * The function reads each line of the file, ignoring comments and blank lines.
     * It extracts the image name and coordinates, then stores them in a list.
     *
     * @param filePath The path to the 'TileConfiguration.txt' file.
     * @return A list of maps, each containing the image name and its coordinates (x, y).
     */
    static def parseTileConfiguration(String filePath) {
        def lines = Files.readAllLines(Paths.get(filePath))
        def imageCoordinates = []

        lines.each { line ->
            if (!line.startsWith("#") && !line.trim().isEmpty()) {
                def parts = line.split(";")
                if (parts.length >= 3) {
                    def imageName = parts[0].trim()
                    def coordinates = parts[2].trim().replaceAll("[()]", "").split(",")
                    imageCoordinates << [imageName: imageName, x: Double.parseDouble(coordinates[0]), y: Double.parseDouble(coordinates[1])]
                }
            }
        }

        return imageCoordinates
    }
    /**
     * Parse an ImageRegion from the TileConfiguration.txt data and TIFF file dimensions.
     * @param imageName Name of the image file for which to get the region.
     * @param tileConfig List of tile configurations parsed from TileConfiguration.txt.
     * @param z index of z plane.
     * @param t index of timepoint.
     * @return An ImageRegion object representing the specified region of the image.
     */
    static ImageRegion parseRegionFromTileConfig(File file, List<Map> tileConfig, int z = 0, int t = 0) {
        String imageName = file.getName()
        def config = tileConfig.find { it.imageName == imageName }

        if (config) {
            int x = config.x as int
            int y = config.y as int
            def dimensions = UtilityFunctions.getTiffDimensions(file)
            if (dimensions == null) {
                logger.info("Could not retrieve dimensions for image $imageName")
                return null
            }
            int width = dimensions.width
            int height = dimensions.height
            //logger.info( x+" "+y+" "+ width+ " " + height)
            return ImageRegion.createInstance(x, y, width, height, z, t)
        } else {
            logger.info("No configuration found for image $imageName")
            return null
        }
    }
}


/**
 * Strategy for stitching images based on Vectra metadata.
 * This class implements the {@link StitchingStrategy} interface, providing an algorithm
 * for processing and stitching images based on metadata from Vectra imaging systems.
 */
class VectraMetadataStrategy implements StitchingStrategy {

    /**
     * Prepares stitching by processing each subdirectory within the specified root directory.
     * This method iterates over each subdirectory that matches the given criteria and
     * aggregates file-region mapping information for stitching.
     *
     * @param folderPath The path to the root directory containing image files.
     * @param pixelSizeInMicrons The pixel size in microns, used for calculating image regions.
     * @param baseDownsample The base downsample value for the stitching process.
     * @param matchingString A string to match for selecting relevant subdirectories.
     * @return A list of maps, each map containing file, region, and subdirName information for stitching.
     */
    @Override
    List<Map> prepareStitching(String folderPath, double pixelSizeInMicrons, double baseDownsample, String matchingString) {
        Path rootdir = Paths.get(folderPath)
        List<Map> allFileRegionMaps = []

        // Iterate over each subdirectory in the root directory
        Files.newDirectoryStream(rootdir).each { path ->
            if (Files.isDirectory(path) && path.fileName.toString().contains(matchingString)) {
                // Process each subdirectory and collect file-region mappings
                def fileRegionMaps = processSubDirectory(path, pixelSizeInMicrons, baseDownsample)
                allFileRegionMaps += fileRegionMaps
            }
        }

        // Check if any valid file-region mappings were found
        if (allFileRegionMaps.isEmpty()) {
            Dialogs.showWarningNotification("Warning", "No valid tile configurations found in any subdirectory.")
            return []
        }

        allFileRegionMaps
    }

    /**
     * Processes a single subdirectory to generate file-region mappings for stitching.
     * This method collects all TIFF files in the directory and creates a mapping of each file
     * to its corresponding image region based on Vectra metadata.
     *
     * @param dir The path to the subdirectory to be processed.
     * @param pixelSizeInMicrons The pixel size in microns for calculating image regions.
     * @param baseDownsample The base downsample value for the stitching process.
     * @return A list of maps, each map containing file, region, and subdirName for stitching.
     */
    private static List<Map> processSubDirectory(Path dir, double pixelSizeInMicrons, double baseDownsample) {
        def logger = LoggerFactory.getLogger(QuPathGUI.class)
        logger.info("Processing slide in folder $dir")

        // Collect all TIFF files in the directory
        List<File> files = []
        Files.newDirectoryStream(dir, "*.tif*").each { path ->
            files << path.toFile()
        }
        logger.info('Parsing regions from ' + files.size() + ' files...')

        // Create file-region mappings for each file
        List<Map> fileRegionMaps = []
        files.each { File file ->
            ImageRegion region = parseRegion(file as File)
            if (region) {
                fileRegionMaps << [file: file, region: region, subdirName: dir.getFileName().toString()]
            }
        }

        return fileRegionMaps
    }

    /**
     * Parses the image region from a given file based on Vectra metadata.
     * This method checks if the file is a TIFF and then parses the region from the TIFF file.
     *
     * @param file The image file for which to parse the region.
     * @param z The z-plane index of the image (default is 0).
     * @param t The timepoint index of the image (default is 0).
     * @return An ImageRegion object representing the specified region of the image, or null if not found.
     */
    static ImageRegion parseRegion(File file, int z = 0, int t = 0) {
        if (checkTIFF(file)) {
            try {
                return parseRegionFromTIFF(file, z, t)
            } catch (Exception e) {
                print e.getLocalizedMessage()
            }
        }
    }

    /**
     * Checks if the provided file is a TIFF image by examining its 'magic number'.
     * TIFF files typically start with a specific byte order indicator (0x4949 for little-endian
     * or 0x4d4d for big-endian), followed by a fixed number (42 or 43).
     *
     * @param file The file to be checked.
     * @return True if the file is a TIFF image, false otherwise.
     */
    static boolean checkTIFF(File file) {
        file.withInputStream {
            def bytes = it.readNBytes(4)
            short byteOrder = toShort(bytes[0], bytes[1])

            // Interpret the next two bytes based on the byte order
            int val
            if (byteOrder == 0x4949) { // Little-endian
                val = toShort(bytes[3], bytes[2])
            } else if (byteOrder == 0x4d4d) { // Big-endian
                val = toShort(bytes[2], bytes[3])
            } else
                return false

            return val == 42 || val == 43 // TIFF magic number
        }
    }

    /**
     * Converts two bytes into a short, in the specified byte order.
     *
     * @param b1 The first byte.
     * @param b2 The second byte.
     * @return The combined short value.
     */
    static short toShort(byte b1, byte b2) {
        return (b1 << 8) + (b2 << 0)
    }

    /**
     * Parses the image region from a TIFF file using metadata information.
     * Reads TIFF metadata to determine the image's physical position and dimensions,
     * then calculates and returns the corresponding ImageRegion object.
     *
     * @param file The TIFF image file to parse.
     * @param z The z-plane index of the image (default is 0).
     * @param t The timepoint index of the image (default is 0).
     * @return An ImageRegion object representing the specified region of the image.
     */
    static ImageRegion parseRegionFromTIFF(File file, int z = 0, int t = 0) {
        int x, y, width, height
        file.withInputStream {
            def reader = ImageIO.getImageReadersByFormatName("TIFF").next()
            reader.setInput(ImageIO.createImageInputStream(it))
            def metadata = reader.getImageMetadata(0)
            def tiffDir = TIFFDirectory.createFromMetadata(metadata)

            // Extract resolution and position values from the metadata
            double xRes = getRational(tiffDir, BaselineTIFFTagSet.TAG_X_RESOLUTION)
            double yRes = getRational(tiffDir, BaselineTIFFTagSet.TAG_Y_RESOLUTION)
            double xPos = getRational(tiffDir, BaselineTIFFTagSet.TAG_X_POSITION)
            double yPos = getRational(tiffDir, BaselineTIFFTagSet.TAG_Y_POSITION)

            // Extract image dimensions from the metadata
            width = tiffDir.getTIFFField(BaselineTIFFTagSet.TAG_IMAGE_WIDTH).getAsLong(0) as int
            height = tiffDir.getTIFFField(BaselineTIFFTagSet.TAG_IMAGE_LENGTH).getAsLong(0) as int

            // Calculate the x and y coordinates in the final stitched image
            x = Math.round(xRes * xPos) as int
            y = Math.round(yRes * yPos) as int
        }
        return ImageRegion.createInstance(x, y, width, height, z, t)
    }

    /**
     * Extracts a rational number from TIFF metadata based on the specified tag.
     * The rational number is represented as a fraction (numerator/denominator).
     *
     * @param tiffDir The TIFFDirectory object containing the metadata.
     * @param tag The metadata tag to extract the rational number from.
     * @return The rational number as a double.
     */
    static double getRational(TIFFDirectory tiffDir, int tag) {
        long[] rational = tiffDir.getTIFFField(tag).getAsRational(0)
        return rational[0] / (double) rational[1]
    }
}


/**
 * Class responsible for managing stitching strategies and executing the stitching process.
 * This class sets the appropriate stitching strategy based on the given type and coordinates the stitching process.
 */
class StitchingImplementations {
    private static StitchingStrategy strategy

    /**
     * Sets the stitching strategy to be used.
     *
     * @param strategy The stitching strategy to be set.
     */
    static void setStitchingStrategy(StitchingStrategy strategy) {
        StitchingImplementations.strategy = strategy
    }

    /**
     * Core method to perform stitching based on the specified stitching type and other parameters.
     * This method determines the stitching strategy, prepares stitching, and then performs the stitching process.
     *
     * @param stitchingType The type of stitching to be performed.
     * @param folderPath The path to the folder containing images to be stitched.
     * @param compressionType The type of compression to be used in the stitching output.
     * @param pixelSizeInMicrons The size of a pixel in microns.
     * @param baseDownsample The base downsample value for the stitching process.
     * @param matchingString A string to match for selecting relevant subdirectories or files.
     */
    static String stitchCore(String stitchingType, String folderPath, String outputPath,
                             String compressionType, double pixelSizeInMicrons,
                             double baseDownsample, String matchingString) {
        def logger = LoggerFactory.getLogger(QuPathGUI.class)
        // Determine the stitching strategy based on the provided type
        logger.info("Stitching type is: $stitchingType")
        switch (stitchingType) {

            case "Filename[x,y] with coordinates in microns":
                setStitchingStrategy(new FileNameStitchingStrategy())
                break
            case "Vectra tiles with metadata":
                setStitchingStrategy(new VectraMetadataStrategy())
                break
            case "Coordinates in TileConfiguration.txt file":
                setStitchingStrategy(new TileConfigurationTxtStrategy())
                break
            default:
                Dialogs.showWarningNotification("Warning", "Error with choosing a stitching method, code here should not be reached in StitchingImplementations.groovy")
                return // Safely exit the method if the stitching type is not recognized
        }

        // Proceed with the stitching process if a valid strategy is set
        if (strategy) {
            // Prepare stitching by processing the folder with the selected strategy
            def fileRegionPairs = strategy.prepareStitching(folderPath, pixelSizeInMicrons, baseDownsample, matchingString)
            OMEPyramidWriter.CompressionType compression = UtilityFunctions.getCompressionType(compressionType)
            def builder = new SparseImageServer.Builder()

            // Check if valid file-region pairs were obtained
            if (fileRegionPairs == null || fileRegionPairs.isEmpty()) {
                Dialogs.showWarningNotification("Warning", "No valid folders found matching the criteria.")
                return // Exit the method if no valid file-region pairs are found
            }

            def subdirName
            // Process each file-region pair to build the image server for stitching
            fileRegionPairs.each { pair ->
                if (pair == null) {
                    logger.warn("Encountered a null pair in fileRegionPairs")
                    return // Skip this iteration if the pair is null
                }
                def file = pair['file'] as File
                def region = pair['region'] as ImageRegion
                subdirName = pair['subdirName'] as String // Extract subdirName from the pair

                if (file == null) {
                    logger.warn("File is null in pair: $pair")
                    return // Skip this iteration if the file is null
                }

                if (region == null) {
                    logger.warn("Region is null in pair: $pair")
                    return // Skip this iteration if the region is null
                }

                // Add the region to the image server builder
                def serverBuilder = ImageServerProvider.getPreferredUriImageSupport(BufferedImage.class, file.toURI().toString()).getBuilders().get(0)
                builder.jsonRegion(region, 1.0, serverBuilder)
            }

            // Build and pyramidalize the server for the final stitched image
            def server = builder.build()
            server = ImageServers.pyramidalize(server)

            // Write the final stitched image
            long startTime = System.currentTimeMillis()
            def filename = subdirName ?: Paths.get(folderPath).getFileName().toString()
            def outputFilePath = baseDownsample == 1 ?
                    Paths.get(outputPath).resolve(filename + '.ome.tif') :
                    Paths.get(outputPath).resolve(filename + '_' + (int) baseDownsample + 'x_downsample.ome.tif')

            //def fileOutput = outputFilePath.toFile()
            //String pathOutput = fileOutput.getAbsolutePath()
            String pathOutput = outputFilePath.toAbsolutePath().toString()
            pathOutput = UtilityFunctions.getUniqueFilePath(pathOutput)
            new OMEPyramidWriter.Builder(server)
                    .tileSize(512)
                    .channelsInterleaved()
                    .parallelize(true)
                    .compression(compression)
                    .scaledDownsampling(baseDownsample, 4)
                    .build()
                    .writePyramid(pathOutput)

            long endTime = System.currentTimeMillis()
            logger.info("Image written to ${pathOutput} in ${GeneralTools.formatNumber((endTime - startTime) / 1000.0, 1)} s")
            server.close()
            return pathOutput
        } else {
            println("No valid stitching strategy set.")
        }
    }
}

