import com.google.gson.*;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;

import java.io.*;
import java.util.*;

public class GraphController {

    private double panAnchorX = 0;
    private double panAnchorY = 0;
    private double currentTranslateX = 0;
    private double currentTranslateY = 0;
    private double scaleFactor = 1.0;

    @FXML private Pane graphCanvas;
    @FXML private Button btnAddNode, btnAddEdge, btnUpdate, btnUpdateEdge;
    @FXML private Button btnDeleteNode, btnDeleteEdge;
    @FXML private Button btnNewGraph, btnSaveGraph, btnLoadGraph;
    @FXML private Button btnClearFields;
    @FXML private Button btnResetCamera;
    @FXML private TextField textFieldId, textFieldType, textFieldLabel, textFieldEdge;
    @FXML private Label statusLabel, graphLabel;

    private final Map<String, GraphNode> nodes = new HashMap<>();
    private final List<GraphEdge> edges = new ArrayList<>();
    private int nodeCounter = 1;
    private GraphNode selectedNode = null;
    private GraphNode secondSelectedNode = null;
    private GraphEdge selectedEdge = null;
    private File currentFile = null;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private static class GraphNode {
        String id, type, label;
        double x, y;
        Circle view;
        Text text;
        List<GraphEdge> connectedEdges = new ArrayList<>();

        GraphNode(String id, String type, String label, double x, double y) {
            this.id = id; this.type = type; this.label = label; this.x = x; this.y = y;
            view = new Circle(x, y, 25);
            view.setFill(Color.CORNFLOWERBLUE);
            view.setStroke(Color.BLACK);
            text = new Text(label);
            text.setMouseTransparent(true);
            updateTextPosition();
            text.layoutBoundsProperty().addListener((obs, o, n) -> updateTextPosition());
            view.setOnMousePressed(e -> view.setUserData(new double[]{e.getSceneX(), e.getSceneY(), this.x, this.y}));
            view.setOnMouseDragged(e -> {
                double[] d = (double[]) view.getUserData();
                this.x = d[2] + (e.getSceneX() - d[0]);
                this.y = d[3] + (e.getSceneY() - d[1]);
                view.setCenterX(this.x);
                view.setCenterY(this.y);
                updateTextPosition();
                for (GraphEdge edge : connectedEdges) edge.updatePosition();
            });
        }

        void updateTextPosition() {
            text.setX(x - text.getLayoutBounds().getWidth() / 2);
            text.setY(y + text.getLayoutBounds().getHeight() / 4);
        }
    }

    private class GraphEdge {
        GraphNode from, to;
        Line line;
        Line arrow1, arrow2;
        Text label;

        private void highlight() {
            line.setStroke(Color.RED);
            arrow1.setStroke(Color.RED);
            arrow2.setStroke(Color.RED);
        }

        private void unhighlight() {
            line.setStroke(Color.GRAY);
            arrow1.setStroke(Color.GRAY);
            arrow2.setStroke(Color.GRAY);
        }

        private void setupClickHandler(javafx.scene.Node node) {
            node.setOnMouseClicked(e -> {
                if (selectedEdge != null) selectedEdge.unhighlight();
                selectedEdge = this;
                highlight();
                textFieldEdge.setText(label.getText());
                statusLabel.setText("Selected edge: " + from.id + " → " + to.id);
            });
        }

        GraphEdge(GraphNode from, GraphNode to, String labelText) {
            this.from = from; this.to = to;
            line = new Line(from.x, from.y, to.x, to.y);
            arrow1 = new Line();
            arrow2 = new Line();
            arrow1.setStroke(Color.GRAY);
            arrow2.setStroke(Color.GRAY);
            line.setStroke(Color.GRAY); line.setStrokeWidth(2);
            label = new Text(labelText);
            setupClickHandler(line);
            setupClickHandler(label);
            setupClickHandler(arrow1);
            setupClickHandler(arrow2);
            label.layoutBoundsProperty().addListener((obs, o, n) -> updatePosition());
            updatePosition();
        }

        void updatePosition() {
            double xx = to.x - from.x;
            double yy = to.y - from.y;
            double lgth = Math.sqrt(xx * xx + yy * yy);

            double ofst = 25; // radius of circle
            double unitX = xx / lgth;
            double unitY = yy / lgth;

            line.setStartX(from.x + unitX * ofst);
            line.setStartY(from.y + unitY * ofst);
            line.setEndX(to.x - unitX * ofst);
            line.setEndY(to.y - unitY * ofst);
            double midX = (from.x + to.x) / 2;
            double midY = (from.y + to.y) / 2;

            // Calculate offset direction perpendicular to the line
            double dx = to.x - from.x;
            double dy = to.y - from.y;
            double length = Math.sqrt(dx * dx + dy * dy);
            double offset = 12; // distance to raise label from the line

            // Normalize and rotate 90 degrees for perpendicular direction
            double nx = -dy / length;
            double ny = dx / length;

            double labelX = midX + nx * offset;
            double labelY = midY + ny * offset;

            label.setX(labelX - label.getLayoutBounds().getWidth() / 2);
            label.setY(labelY + label.getLayoutBounds().getHeight() / 4);

            // === Arrowhead positioning ===
            double angle = Math.atan2(yy, xx);
            double arrowLength = 10;
            double arrowAngle = Math.toRadians(25);

            // Arrow base (where the arrow connects to the edge)
            double arrowBaseX = to.x - unitX * ofst;
            double arrowBaseY = to.y - unitY * ofst;

            // Two points for the arrowhead "V"
            double x1 = arrowBaseX - arrowLength * Math.cos(angle - arrowAngle);
            double y1 = arrowBaseY - arrowLength * Math.sin(angle - arrowAngle);

            double x2 = arrowBaseX - arrowLength * Math.cos(angle + arrowAngle);
            double y2 = arrowBaseY - arrowLength * Math.sin(angle + arrowAngle);

            // Apply to lines
            arrow1.setStartX(arrowBaseX);
            arrow1.setStartY(arrowBaseY);
            arrow1.setEndX(x1);
            arrow1.setEndY(y1);

            arrow2.setStartX(arrowBaseX);
            arrow2.setStartY(arrowBaseY);
            arrow2.setEndX(x2);
            arrow2.setEndY(y2);
        }
    }

    @FXML
    public void initialize() {
        btnAddNode.setOnAction(e -> handleAddNode());
        btnAddEdge.setOnAction(e -> handleAddEdge());
        btnUpdate.setOnAction(e -> handleUpdateNode());
        btnUpdateEdge.setOnAction(e -> handleUpdateEdge());
        btnDeleteNode.setOnAction(e -> handleDeleteNode());
        btnDeleteEdge.setOnAction(e -> handleDeleteEdge());
        btnNewGraph.setOnAction(e -> handleNewGraph());
        btnSaveGraph.setOnAction(e -> handleSaveGraph());
        btnLoadGraph.setOnAction(e -> handleLoadGraph());
        btnClearFields.setOnAction(e -> handleClearFields());
        graphCanvas.setOnMousePressed(event -> {
            if (event.getTarget() == graphCanvas) {
                panAnchorX = event.getSceneX();
                panAnchorY = event.getSceneY();
            }
        });
        graphCanvas.setOnMouseDragged(event -> {
            if (event.getTarget() == graphCanvas) {
                double deltaX = event.getSceneX() - panAnchorX;
                double deltaY = event.getSceneY() - panAnchorY;

                graphCanvas.setTranslateX(currentTranslateX + deltaX);
                graphCanvas.setTranslateY(currentTranslateY + deltaY);
            }
        });
        graphCanvas.setOnMouseReleased(event -> {
            if (event.getTarget() == graphCanvas) {
                currentTranslateX = graphCanvas.getTranslateX();
                currentTranslateY = graphCanvas.getTranslateY();
            }
        });
        graphCanvas.setOnScroll(event -> {
            double zoomFactor = 1.1;
            if (event.getDeltaY() < 0) {
                scaleFactor /= zoomFactor;
            } else {
                scaleFactor *= zoomFactor;
            }

            graphCanvas.setScaleX(scaleFactor);
            graphCanvas.setScaleY(scaleFactor);
        });
        btnResetCamera.setOnAction(e -> resetCamera());
        graphLabel.setText("Current Graph: None");
        statusLabel.setText("Ready");
    }

    private void resetCamera() {
        scaleFactor = 1.0;
        graphCanvas.setScaleX(scaleFactor);
        graphCanvas.setScaleY(scaleFactor);

        currentTranslateX = 0;
        currentTranslateY = 0;
        graphCanvas.setTranslateX(currentTranslateX);
        graphCanvas.setTranslateY(currentTranslateY);

        statusLabel.setText("Camera reset.");
    }

    private void handleAddNode() {
        String id = textFieldId.getText().trim();
        String type = textFieldType.getText().trim();
        String label = textFieldLabel.getText().trim();
        if (id.isEmpty()) id = "N" + nodeCounter;
        if (nodes.containsKey(id)) {
            statusLabel.setText("Node ID '" + id + "' already exists.");
            return;
        }

        double paneW = graphCanvas.getWidth(), paneH = graphCanvas.getHeight();
        double r = 25, pad = 20, space = 2 * r + pad;
        int maxCols = (int)((paneW - pad) / space);
        int maxRows = (int)((paneH - pad) / space);
        if (maxCols < 1) maxCols = 1;
        if (maxRows < 1) maxRows = 1;

        Set<String> occupied = new HashSet<>();
        for (GraphNode n : nodes.values()) {
            int gx = (int)(n.x / space), gy = (int)(n.y / space);
            occupied.add(gx + "," + gy);
        }

        int row = 0, col = 0;
        outer: for (int rIdx = 0; rIdx < maxRows; rIdx++) {
            for (int cIdx = 0; cIdx < maxCols; cIdx++) {
                if (!occupied.contains(cIdx + "," + rIdx)) {
                    row = rIdx; col = cIdx; break outer;
                }
            }
        }

        double x = pad + col * space + r;
        double y = pad + row * space + r;
        nodeCounter++;

        GraphNode node = new GraphNode(id, type, label, x, y);
        nodes.put(id, node);
        graphCanvas.getChildren().addAll(node.view, node.text);

        node.view.setOnMouseClicked(event -> {
            if (selectedNode == null) {
                selectedNode = node; node.view.setStroke(Color.RED);
            } else if (secondSelectedNode == null && node != selectedNode) {
                secondSelectedNode = node; node.view.setStroke(Color.GREEN);
            } else clearSelection();
            populateNodeInfo(node);
        });

        statusLabel.setText("Node " + id + " added");
        clearTextFields();
    }

    private void handleAddEdge() {
        if (selectedNode != null && secondSelectedNode != null) {
            String edgeLabel = textFieldEdge.getText().trim();
            GraphEdge edge = new GraphEdge(selectedNode, secondSelectedNode, edgeLabel);
            edges.add(edge);
            selectedNode.connectedEdges.add(edge);
            secondSelectedNode.connectedEdges.add(edge);
            graphCanvas.getChildren().addAll(edge.line, edge.arrow1, edge.arrow2, edge.label);
            statusLabel.setText("Edge created from " + selectedNode.id + " to " + secondSelectedNode.id);
            clearSelection();
            clearEdgeField();
        } else statusLabel.setText("Select two nodes first.");
    }

    private void handleUpdateNode() {
        if (selectedNode != null) {
            String newId = textFieldId.getText().trim();
            String newType = textFieldType.getText().trim();
            String newLabel = textFieldLabel.getText().trim();

            if (!newId.equals(selectedNode.id) && nodes.containsKey(newId)) {
                statusLabel.setText("Node ID '" + newId + "' already exists.");
                return;
            }

            if (!newId.equals(selectedNode.id)) {
                nodes.remove(selectedNode.id);         // remove old ID
                selectedNode.id = newId;               // update object
                nodes.put(newId, selectedNode);        // insert new ID
            }

            selectedNode.type = newType;
            selectedNode.label = newLabel;
            selectedNode.text.setText(newLabel);
            selectedNode.updateTextPosition();
            statusLabel.setText("Updated node: " + selectedNode.id);
        } else {
            statusLabel.setText("No node selected to update.");
        }
    }

    private void handleUpdateEdge() {
        if (selectedEdge != null) {
            selectedEdge.label.setText(textFieldEdge.getText().trim());
            statusLabel.setText("Edge label updated.");
        } else statusLabel.setText("No edge selected.");
    }

    private void handleDeleteNode() {
        if (selectedNode != null) {
            List<GraphEdge> toRemove = new ArrayList<>(selectedNode.connectedEdges);
            for (GraphEdge edge : toRemove) removeEdge(edge);
            graphCanvas.getChildren().removeAll(selectedNode.view, selectedNode.text);
            nodes.remove(selectedNode.id);
            selectedNode = null; secondSelectedNode = null;
            statusLabel.setText("Node deleted.");
            clearTextFields();
        } else statusLabel.setText("No node selected.");
    }

    private void handleDeleteEdge() {
        if (selectedEdge != null) {
            removeEdge(selectedEdge);
            selectedEdge = null;
            statusLabel.setText("Edge deleted.");
            clearEdgeField();
        } else statusLabel.setText("No edge selected.");
    }

    private void removeEdge(GraphEdge edge) {
        graphCanvas.getChildren().removeAll(edge.line, edge.arrow1, edge.arrow2, edge.label);
        edges.remove(edge);
        edge.from.connectedEdges.remove(edge);
        edge.to.connectedEdges.remove(edge);
    }

    private void populateNodeInfo(GraphNode node) {
        textFieldId.setText(node.id);
        textFieldType.setText(node.type);
        textFieldLabel.setText(node.label);
    }

    private void clearSelection() {
        if (selectedNode != null) selectedNode.view.setStroke(Color.BLACK);
        if (secondSelectedNode != null) secondSelectedNode.view.setStroke(Color.BLACK);
        if (selectedEdge != null) selectedEdge.unhighlight();
        selectedNode = null;
        secondSelectedNode = null;
        selectedEdge = null;
    }

    private void handleNewGraph() {
        clearGraph();
        currentFile = null;
        graphLabel.setText("Current Graph: None");
        statusLabel.setText("New graph started.");
    }

    private void handleSaveGraph() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Graph");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File file = fc.showSaveDialog(graphCanvas.getScene().getWindow());
        if (file != null) {
            saveGraphToFile(file);
            currentFile = file;
            graphLabel.setText("Current Graph: " + file.getName());
            statusLabel.setText("Graph saved.");
        }
    }

    private void handleLoadGraph() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Load Graph");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON", "*.json"));
        File file = fc.showOpenDialog(graphCanvas.getScene().getWindow());
        if (file != null) {
            loadGraphFromFile(file);
            currentFile = file;
            graphLabel.setText("Current Graph: " + file.getName());
            statusLabel.setText("Graph loaded.");
            clearTextFields();
            clearEdgeField();
        }
    }

    private void saveGraphToFile(File file) {
        try (FileWriter writer = new FileWriter(file)) {
            JsonObject root = new JsonObject();
            JsonArray nArr = new JsonArray();
            for (GraphNode n : nodes.values()) {
                JsonObject o = new JsonObject();
                o.addProperty("id", n.id); o.addProperty("type", n.type);
                o.addProperty("label", n.label);
                o.addProperty("x", n.x); o.addProperty("y", n.y);
                nArr.add(o);
            }

            JsonArray eArr = new JsonArray();
            for (GraphEdge e : edges) {
                JsonObject o = new JsonObject();
                o.addProperty("from", e.from.id); o.addProperty("to", e.to.id);
                o.addProperty("label", e.label.getText());
                eArr.add(o);
            }

            root.add("nodes", nArr); root.add("edges", eArr);
            gson.toJson(root, writer);
        } catch (IOException e) {
            statusLabel.setText("Failed to save graph.");
            e.printStackTrace();
        }
    }

    private void loadGraphFromFile(File file) {
        clearGraph();
        try (FileReader reader = new FileReader(file)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            for (JsonElement el : root.getAsJsonArray("nodes")) {
                JsonObject o = el.getAsJsonObject();
                GraphNode node = new GraphNode(
                        o.get("id").getAsString(),
                        o.get("type").getAsString(),
                        o.get("label").getAsString(),
                        o.get("x").getAsDouble(),
                        o.get("y").getAsDouble()
                );
                nodes.put(node.id, node);
                graphCanvas.getChildren().addAll(node.view, node.text);
                node.view.setOnMouseClicked(e -> {
                    if (selectedNode == null) {
                        selectedNode = node; node.view.setStroke(Color.RED);
                    } else if (secondSelectedNode == null && node != selectedNode) {
                        secondSelectedNode = node; node.view.setStroke(Color.GREEN);
                    } else clearSelection();
                    populateNodeInfo(node);
                });
            }

            for (JsonElement el : root.getAsJsonArray("edges")) {
                JsonObject o = el.getAsJsonObject();
                GraphNode from = nodes.get(o.get("from").getAsString());
                GraphNode to = nodes.get(o.get("to").getAsString());
                if (from != null && to != null) {
                    GraphEdge edge = new GraphEdge(from, to, o.get("label").getAsString());
                    edges.add(edge);
                    from.connectedEdges.add(edge); to.connectedEdges.add(edge);
                    graphCanvas.getChildren().addAll(edge.line, edge.arrow1, edge.arrow2, edge.label);
                }
            }

        } catch (IOException e) {
            statusLabel.setText("Failed to load graph.");
            e.printStackTrace();
        }
    }

    private void clearGraph() {
        graphCanvas.getChildren().clear();
        nodes.clear(); edges.clear();
        clearSelection();
        clearTextFields();
        clearEdgeField();
        nodeCounter = 1;
    }

    private void clearTextFields() {
        textFieldId.clear();
        textFieldType.clear();
        textFieldLabel.clear();
    }

    private void clearEdgeField() {
        textFieldEdge.clear();
    }

    private void handleClearFields() {
        clearTextFields();
        clearEdgeField();
        statusLabel.setText("Fields cleared.");
    }
}
