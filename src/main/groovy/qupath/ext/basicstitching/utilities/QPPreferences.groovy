package qupath.ext.basicstitching.utilities

import javafx.beans.property.ObjectProperty
import javafx.beans.property.StringProperty
import qupath.lib.gui.prefs.PathPrefs
import qupath.lib.images.writers.ome.OMEPyramidWriter

class QPPreferences {

}

class AutoFillPersistentPreferences{

    //private AutoFillPersistentPreferences options = AutoFillPersistentPreferences.getInstance();
    private static StringProperty folderLocationSaved = PathPrefs.createPersistentPreference("folderLocation", "C:/");

    public static String getFolderLocationSaved(){
        return folderLocationSaved.value
    }
    public static void setFolderLocationSaved(final String folderLocationSaved){
        this.folderLocationSaved.value = folderLocationSaved
    }

    private static StringProperty imagePixelSizeInMicronsSaved = PathPrefs.createPersistentPreference("imagePixelSizeInMicrons", "7.2");

    public static String getImagePixelSizeInMicronsSaved(){
        return imagePixelSizeInMicronsSaved.value
    }
    public static void setImagePixelSizeInMicronsSaved(final String imagePixelSizeInMicronsSaved){
        this.imagePixelSizeInMicronsSaved.value = imagePixelSizeInMicronsSaved
    }

    private static StringProperty downsampleSaved = PathPrefs.createPersistentPreference("downsample", "1");

    public static String getDownsampleSaved(){
        return downsampleSaved.value
    }
    public static void setDownsampleSaved(final String downsampleSaved){
        this.downsampleSaved.value = downsampleSaved
    }

    private static StringProperty searchStringSaved = PathPrefs.createPersistentPreference("searchString", "20x");

    public static String getSearchStringSaved(){
        return searchStringSaved.value
    }
    public static void setSearchStringSaved(final String searchStringSaved){
        this.searchStringSaved.value = searchStringSaved
    }

    private static StringProperty compressionTypeSaved = PathPrefs.createPersistentPreference("compressionType","J2K")

    public static String getCompressionTypeSaved(){
        return compressionTypeSaved.value
    }
    public static void setCompressionTypeSaved(final String compressionTypeSaved){
        this.compressionTypeSaved.value = compressionTypeSaved
    }

    private static StringProperty stitchingMethodSaved = PathPrefs.createPersistentPreference("stitchingMethod","Coordinates in TileConfiguration.txt file")

    public static String getStitchingMethodSaved(){
        return stitchingMethodSaved.value
    }
    public static void setStitchingMethodSaved(final String stitchingMethodSaved){
        this.stitchingMethodSaved.value = stitchingMethodSaved
    }

}