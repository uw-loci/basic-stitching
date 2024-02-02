package qupath.ext.basicstitching.utilities

import org.slf4j.LoggerFactory
import qupath.lib.gui.QuPathGUI
import qupath.lib.images.writers.ome.OMEPyramidWriter

import javax.imageio.ImageIO
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Class containing utility functions used throughout the application.
 */
class UtilityFunctions {
    static ArrayList<String> getCompressionTypeList() {
        def compressionTypeClass = OMEPyramidWriter.CompressionType

// Retrieve all declared fields in the class
        def fields = compressionTypeClass.declaredFields

// Filter only public static final fields
        def compressionTypes = fields.findAll {
            Modifier.isPublic(it.modifiers) &&
                    Modifier.isStatic(it.modifiers) &&
                    Modifier.isFinal(it.modifiers)
        }

// Extract the names of the fields
        def compressionTypeNames = compressionTypes*.name
        return compressionTypeNames
    }
    /**
     * Gets the compression type for OMEPyramidWriter based on the selected option.
     *
     * @param selectedOption The selected compression option as a string.
     * @return The corresponding OMEPyramidWriter.CompressionType.
     * @throws IllegalArgumentException if the selected option does not match any compression type.
     */
    static OMEPyramidWriter.CompressionType getCompressionType(String selectedOption) throws IllegalArgumentException {
        try {
            // Convert the string to an enum constant
            return OMEPyramidWriter.CompressionType.valueOf(selectedOption);
        } catch (IllegalArgumentException e) {
            // Throw an exception if no matching compression type is found
            throw new IllegalArgumentException("Invalid compression type: " + selectedOption);
        }
    }
/**
 * Generates a unique file path by appending a number to the file name if a file with the
 * same name already exists. The first instance of the file will have no number appended,
 * while the second will have _2, the third _3, and so on.
 *
 * @param originalPath The original file path.
 * @return A unique file path.
 */
    static String getUniqueFilePath(String originalPath) {
        Path path = Paths.get(originalPath);
        String baseName = path.getFileName().toString().replaceAll('\\.ome\\.tif$', "");
        Path parentDir = path.getParent();

        // Start with the original base name
        Path newPath = parentDir.resolve(baseName + ".ome.tif");
        int counter = 2;

        // If the file exists, start appending numbers
        while (Files.exists(newPath)) {
            newPath = parentDir.resolve(baseName + "_" + counter + ".ome.tif");
            counter++;
        }

        return newPath.toString();
    }

    /**
     * Retrieves the dimensions (width and height) of a TIFF image file.
     *
     * @param filePath The file path of the TIFF image.
     * @return A map containing the 'width' and 'height' of the image, or null if an error occurs.
     */
    static Map<String, Integer> getTiffDimensions(File filePath) {
        def logger = LoggerFactory.getLogger(QuPathGUI.class)

        // Check if the file exists
        if (!filePath.exists()) {
            logger.info("File not found: $filePath")
            return null
        }

        try {
            // Read the image file
            def image = ImageIO.read(filePath)
            if (image == null) {
                logger.info("ImageIO returned null for file: $filePath")
                return null
            }

            // Return the image dimensions as a map
            return [width: image.getWidth(), height: image.getHeight()]
        } catch (IOException e) {
            // Log and handle the error
            logger.info("Error reading the image file $filePath: ${e.message}")
            return null
        }
    }
}

//TODO Move this somewhere
//    List<Map> prepareStitching(String folderPath, double pixelSizeInMicrons, double baseDownsample, String matchingString) {
//        def logger = LoggerFactory.getLogger(QuPathGUI.class)
//        Path rootdir = Paths.get(folderPath)
//        List<Map> allFileRegionMaps = [] // This will store the file-region maps for all subdirectories
//        def subdir = []
//
//        Files.newDirectoryStream(rootdir).each { path ->
//            if (Files.isDirectory(path) && path.fileName.toString().contains(matchingString)) {
//                logger.info("Processing: $path")
//                def fileRegionMaps = processSubDirectory(path, pixelSizeInMicrons, baseDownsample)
//                allFileRegionMaps += fileRegionMaps
//            }
//        }
//
//        if (allFileRegionMaps.isEmpty()) {
//            Dialogs.showWarningNotification("Warning", "No valid tile configurations found in any subdirectory.")
//            return
//        }
//
//        allFileRegionMaps
//    }
//}
