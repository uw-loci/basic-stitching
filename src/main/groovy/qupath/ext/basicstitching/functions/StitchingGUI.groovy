// TODO Progress bar for stitching
// TODO Estimate size of stitched image to predict necessary memory
// Warn user if size exceeds QuPath's allowed limits.

package qupath.ext.basicstitching.functions

import javafx.scene.Node
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.stage.DirectoryChooser
import javafx.stage.Modality
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import qupath.ext.basicstitching.stitching.StitchingImplementations
import qupath.lib.gui.scripting.QPEx

import java.awt.*

import static qupath.ext.basicstitching.utilities.UtilityFunctions.getCompressionTypeList

class StitchingGUI {

    private static final Logger logger = LoggerFactory.getLogger(StitchingGUI.class);
    static TextField folderField = new TextField();
    static ComboBox<String> compressionBox = new ComboBox<>();
    static TextField pixelSizeField = new TextField("0.4988466");
    static TextField downsampleField = new TextField("1");
    static TextField matchStringField = new TextField("20x");
    static ComboBox<String> stitchingGridBox = new ComboBox<>(); // New combo box for stitching grid options
    static Button folderButton = new Button("Select Folder");
    // Declare labels as static fields
    static Label stitchingGridLabel = new Label("Stitching Method:");
    static Label folderLabel = new Label("Folder location:");
    static Label compressionLabel = new Label("Compression type:");
    static Label pixelSizeLabel = new Label("Pixel size, microns:");
    static Label downsampleLabel = new Label("Downsample:");
    static Label matchStringLabel = new Label("Stitch sub-folders with text string:");
    static Hyperlink githubLink = new Hyperlink("GitHub ReadMe");

    // Map to hold the positions of each GUI element
    private static Map<Node, Integer> guiElementPositions = new HashMap<>();


    static void createGUI() {
        // Create the dialog
        def dlg = new Dialog<ButtonType>()
        dlg.initModality(Modality.APPLICATION_MODAL)
        dlg.setTitle("Input Stitching Method and Options")
        dlg.setHeaderText("Enter your settings below:")

        // Set the content
        dlg.getDialogPane().setContent(createContent())

        // Add Okay and Cancel buttons
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL)

        // Show the dialog and capture the response
        def result = dlg.showAndWait()

        // Handling the response
        if (result.isPresent() && result.get() == ButtonType.OK) {
            String folderPath = folderField.getText() // Assuming folderField is accessible
            String compressionType = compressionBox.getValue() // Assuming compressionBox is accessible

            // Check if pixelSizeField and downsampleField are not empty
            double pixelSize = pixelSizeField.getText() ? Double.parseDouble(pixelSizeField.getText()) : 0
            // Default to 0 if empty
            double downsample = downsampleField.getText() ? Double.parseDouble(downsampleField.getText()) : 1
            // Default to 1 if empty

            String matchingString = matchStringField.getText() // Assuming matchStringField is accessible
            String stitchingType = stitchingGridBox.getValue()
            // Call the function with collected data
            String finalImageName = StitchingImplementations.stitchCore(stitchingType, folderPath, folderPath, compressionType, pixelSize, downsample, matchingString)
            //stitchByFileName(folderPath, compressionType, pixelSize, downsample, matchingString)
        }

    }

/**
 * Creates and returns a GridPane containing all the components for the GUI.
 * This method initializes the positions of each component in the grid,
 * adds the components to the grid, and sets their initial visibility.
 *
 * @return A GridPane containing all the configured components.
 */
    private static GridPane createContent() {
        // Create a new GridPane for layout
        GridPane pane = new GridPane();

        // Set horizontal and vertical gaps between grid cells
        pane.setHgap(10);
        pane.setVgap(10);

        // Initialize the positions of each component in the grid
        initializePositions();

        // Add various components to the grid pane
        // Each method call below corresponds to a specific component or a group of components
        addStitchingGridComponents(pane);  // Adds a combo box for selecting the stitching method
        addFolderSelectionComponents(pane); // Adds components for selecting the folder location
        addMatchStringComponents(pane);     // Adds a text field for entering the matching string
        addCompressionComponents(pane);     // Adds a combo box for selecting the compression type
        addPixelSizeComponents(pane);       // Adds a text field for entering the pixel size
        addDownsampleComponents(pane);      // Adds a text field for entering the downsample value

        // Add a hyperlink to the GitHub repository at the bottom of the pane
        addGitHubLinkComponent(pane);

        // Update the components' visibility based on the current selection in the stitching method combo box
        updateComponentsBasedOnSelection(pane);

        // Return the fully configured GridPane
        return pane;
    }


/**
 * Adds a label and its associated control to the specified GridPane.
 * The method uses the guiElementPositions map to determine the correct
 * row index for the label and control in the grid. If the row index is
 * not found, an error is logged.
 *
 * @param pane The GridPane to which the label and control are added.
 * @param label The label to be added to the grid.
 * @param control The control (e.g., TextField, ComboBox) associated with the label.
 */
    private static void addToGrid(GridPane pane, Node label, Node control) {
        // Retrieve the row index for the label from the guiElementPositions map
        Integer rowIndex = guiElementPositions.get(label);

        // Check if the row index was found
        if (rowIndex != null) {
            // Add the label and control to the grid at the specified row index
            pane.add(label, 0, rowIndex);  // Add label to column 0
            pane.add(control, 1, rowIndex); // Add control to column 1
        } else {
            // Log an error if the row index is not found
            logger.error("Row index not found for component: " + label);
        }
    }

/**
 * Adds a GitHub repository hyperlink to the GridPane. The hyperlink is configured to open
 * the GitHub page in the default web browser when clicked. The position of the hyperlink in the
 * GridPane is determined based on its predefined row index in the guiElementPositions map.
 *
 * @param pane The GridPane to which the GitHub hyperlink is to be added.
 */
    private static void addGitHubLinkComponent(GridPane pane) {
        // Set up the action on the hyperlink to open the GitHub repository URL
        // in the user's default web browser when clicked.
        githubLink.setOnAction(e -> {
            try {
                // Open the GitHub repository URL
                Desktop.getDesktop().browse(new URI("https://github.com/MichaelSNelson/BasicStitching"));
            } catch (Exception ex) {
                // Log any error encountered while trying to open the URL
                logger.error("Error opening link", ex);
            }
        });

        // Retrieve the pre-defined row index for the hyperlink from the guiElementPositions map.
        // This determines where the hyperlink will be placed in the GridPane.
        Integer rowIndex = guiElementPositions.get(githubLink);

        // Add the hyperlink to the specified row in the GridPane, spanning across 2 columns.
        pane.add(githubLink, 0, rowIndex, 2, 1);
    }


/**
 * Initializes the positions of GUI elements in the GridPane. This method assigns a unique
 * row index to each GUI element by incrementally increasing a position counter. The positions
 * are stored in the guiElementPositions map, which maps each GUI element (Node) to its row index
 * in the GridPane.
 */
    private static void initializePositions() {
        // Start with a position counter at 0.
        int currentPosition = 0;

        // Dynamically assign row positions to each GUI element.
        // The order of these statements dictates their vertical order in the GridPane.

        guiElementPositions.put(stitchingGridLabel, currentPosition++);
        // Position for stitching grid label and combo box
        guiElementPositions.put(folderLabel, currentPosition++);         // Position for folder label and text field
        guiElementPositions.put(compressionLabel, currentPosition++);   // Position for compression label and combo box
        guiElementPositions.put(pixelSizeLabel, currentPosition++);     // Position for pixel size label and text field
        guiElementPositions.put(downsampleLabel, currentPosition++);    // Position for downsample label and text field
        guiElementPositions.put(matchStringLabel, currentPosition++);
        // Position for matching string label and text field
        guiElementPositions.put(githubLink, currentPosition++);         // Position for the GitHub hyperlink

        // More components can be added here following the same pattern.
    }


/**
 * Adds stitching grid components to the specified GridPane. This method configures a combo box
 * for selecting the stitching method and adds it along with its label to the GridPane.
 * It clears any existing items in the combo box and adds a predefined set of stitching options.
 * The default stitching method is set, and an action is defined to update component visibility
 * based on the selected stitching method.
 *
 * @param pane The GridPane to which the stitching grid components are to be added.
 */
    private static void addStitchingGridComponents(GridPane pane) {
        // Clear any existing items in the combo box to avoid duplicates
        stitchingGridBox.getItems().clear();

        // Add a set of predefined stitching options to the combo box
        stitchingGridBox.getItems().addAll(
                "Vectra tiles with metadata",
                "Filename[x,y] with coordinates in microns",
                "Coordinates in TileConfiguration.txt file"
        );

        // Set the default value for the combo box
        stitchingGridBox.setValue("Coordinates in TileConfiguration.txt file");

        // Define an action to be performed when a new item is selected in the combo box
        // This action updates the visibility of other components based on the selection
        stitchingGridBox.setOnAction(e -> updateComponentsBasedOnSelection(pane));

        // Add the stitching method label and the combo box to the GridPane
        // using the addToGrid helper method
        addToGrid(pane, stitchingGridLabel as Node, stitchingGridBox as Node);
    }


/**
 * Adds components for folder selection to the specified GridPane. This method configures a text field
 * for displaying the selected folder path and a button to open a directory chooser dialog.
 * It attempts to set a default folder path and defines the action for the button to select a folder.
 * The text field and the button are added to the GridPane in their designated positions.
 *
 * @param pane The GridPane to which the folder selection components are to be added.
 */
    private static void addFolderSelectionComponents(GridPane pane) {
        // Attempt to initialize the folder path text field with a default path
        try {
            String defaultFolderPath = QPEx.buildPathInProject("Tiles");
            logger.info("Default folder path: {}", defaultFolderPath);
            folderField.setText(defaultFolderPath);
        } catch (Exception e) {
            // If the default path cannot be set, probably due to no project being open
            logger.info("Error setting default folder path, usually due to no project being open", e);
        }

        // Configure the action for the folder selection button
        folderButton.setOnAction(e -> {
            try {
                DirectoryChooser dirChooser = new DirectoryChooser();
                dirChooser.setTitle("Select Folder");

                // Get the initial directory path from the text field
                String initialDirPath = folderField.getText();
                File initialDir = new File(initialDirPath);

                // Set the initial directory in the directory chooser if it exists
                if (initialDir.exists() && initialDir.isDirectory()) {
                    dirChooser.setInitialDirectory(initialDir);
                } else {
                    logger.warn("Initial directory does not exist or is not a directory: {}", initialDir.getAbsolutePath());
                }

                // Show the directory chooser dialog and update the text field with the selected directory
                File selectedDir = dirChooser.showDialog(null); // Replace null with your stage if available
                if (selectedDir != null) {
                    folderField.setText(selectedDir.getAbsolutePath());
                    logger.info("Selected folder path: {}", selectedDir.getAbsolutePath());
                }
            } catch (Exception ex) {
                // Log an error if there is an issue during folder selection
                logger.error("Error selecting folder", ex);
            }
        });

        // Add the folder label and text field to the GridPane
        addToGrid(pane, folderLabel as Node, folderField as Node);

        // Retrieve the row index for placing the folder button and add it to the GridPane
        Integer rowIndex = guiElementPositions.get(folderLabel);
        if (rowIndex != null) {
            pane.add(folderButton, 2, rowIndex); // Place the button next to the text field
        } else {
            // Log an error if the row index for the folder button is not found
            logger.error("Row index not found for folderButton");
        }
    }

//TODO populate with a list of compression types
/**
 * Adds compression selection components to the specified GridPane.
 * This method configures a combo box with options for different types of image compression
 * and adds it to the grid along with a label. A tooltip is also set for the label and
 * the combo box to provide additional information to the user.
 *
 * @param pane The GridPane to which the compression components are to be added.
 */
    private static void addCompressionComponents(GridPane pane) {
        // Clear any existing items and add new compression options to the combo box
        def compressionTypes = getCompressionTypeList()
        compressionBox.getItems().clear();
        compressionBox.getItems().addAll(compressionTypes);

        // Set the default value for the combo box
        compressionBox.setValue("J2K_LOSSY");

        // Create and set a tooltip for additional information
        Tooltip compressionTooltip = new Tooltip("Select the type of image compression.");
        compressionLabel.setTooltip(compressionTooltip);
        compressionBox.setTooltip(compressionTooltip);

        // Add the compression label and combo box to the GridPane
        addToGrid(pane, compressionLabel as Node, compressionBox as Node);
    }


/**
 * Adds pixel size input components to the specified GridPane.
 * This method adds a label and a text field for entering the pixel size to the grid.
 *
 * @param pane The GridPane to which the pixel size components are to be added.
 */
    private static void addPixelSizeComponents(GridPane pane) {
        // Add the pixel size label and text field to the GridPane
        addToGrid(pane, pixelSizeLabel as Node, pixelSizeField as Node);
    }


/**
 * Adds downsample input components to the specified GridPane.
 * This method adds a label and a text field for entering the downsample value to the grid.
 * A tooltip is also set for the label and the text field to provide additional information.
 *
 * @param pane The GridPane to which the downsample components are to be added.
 */
    private static void addDownsampleComponents(GridPane pane) {
        // Create and set a tooltip for additional information
        Tooltip downsampleTooltip = new Tooltip("The amount by which the highest resolution plane will be initially downsampled.");
        downsampleLabel.setTooltip(downsampleTooltip);
        downsampleField.setTooltip(downsampleTooltip);

        // Add the downsample label and text field to the GridPane
        addToGrid(pane, downsampleLabel as Node, downsampleField as Node);
    }


/**
 * Adds matching string input components to the specified GridPane.
 * This method adds a label and a text field for entering the matching string to the grid.
 *
 * @param pane The GridPane to which the matching string components are to be added.
 */
    private static void addMatchStringComponents(GridPane pane) {
        // Add the matching string label and text field to the GridPane
        addToGrid(pane, matchStringLabel as Node, matchStringField as Node);
    }


/**
 * Updates the visibility of certain GUI components based on the current selection
 * in the stitching method combo box. Specifically, it hides or shows the pixel size
 * field and label based on the selected stitching method.
 *
 * @param pane The GridPane containing the components to be updated.
 */
    private static void updateComponentsBasedOnSelection(GridPane pane) {
        // Determine whether to hide the pixel size field and label
        boolean hidePixelSize = stitchingGridBox.getValue().equals("Vectra multiplex tif") ||
                stitchingGridBox.getValue().equals("Coordinates in TileCoordinates.txt file");
        pixelSizeLabel.setVisible(!hidePixelSize);
        pixelSizeField.setVisible(!hidePixelSize);

        // Adjust the layout of the GridPane to reflect the visibility changes
        adjustLayout(pane);
    }


/**
 * Adjusts the layout of the GridPane based on the current positions specified in the guiElementPositions map.
 * This method iterates through each GUI element in the map and updates its row index in the GridPane.
 * The adjustment ensures that the GUI elements are displayed in the correct order and position,
 * especially after any visibility changes.
 *
 * @param pane The GridPane whose layout is to be adjusted.
 */
    private static void adjustLayout(GridPane pane) {
        // Iterate through each entry in the guiElementPositions map
        for (Map.Entry<Node, Integer> entry : guiElementPositions.entrySet()) {
            Node node = entry.getKey();       // The GUI element (Node)
            Integer newRow = entry.getValue(); // The new row index for the element

            // Check if the node is a part of the GridPane's children
            if (pane.getChildren().contains(node)) {
                // Update the node's row index in the GridPane
                GridPane.setRowIndex(node, newRow);
            }
        }
    }


}
