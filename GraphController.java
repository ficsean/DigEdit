import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;

import java.util.*;

public class GraphController {

    @FXML private Pane graphCanvas;
    @FXML private Button btnAddNode;
    @FXML private Button btnAddEdge;
    @FXML private Button btnUpdate;
    @FXML private Button btnUpdateEdge;
    @FXML private Button btnDeleteNode;
    @FXML private Button btnDeleteEdge;
    @FXML private TextField textFieldId;
    @FXML private TextField textFieldType;
    @FXML private TextField textFieldLabel;
    @FXML private TextField textFieldEdge;
    @FXML private Label statusLabel;

    private final Map<String, GraphNode> nodes = new HashMap<>();
    private final List<GraphEdge> edges = new ArrayList<>();

    private int nodeCounter = 1;
    private GraphNode selectedNode = null;
    private GraphNode secondSelectedNode = null;
    private GraphEdge selectedEdge = null;

    // === NODE CLASS ===
    private static class GraphNode {
        String id, type, label;
        double x, y;
        Circle view;
        Text text;
        List<GraphEdge> connectedEdges = new ArrayList<>();

        GraphNode(String id, String type, String label, double x, double y) {
            this.id = id;
            this.type = type;
            this.label = label;
            this.x = x;
            this.y = y;

            view = new Circle(x, y, 25);
            view.setFill(Color.CORNFLOWERBLUE);
            view.setStroke(Color.BLACK);

            text = new Text(label);
            text.setMouseTransparent(true);
            updateTextPosition();

            text.layoutBoundsProperty().addListener((obs, oldVal, newVal) -> updateTextPosition());

            view.setOnMousePressed(e -> {
                view.setUserData(new double[]{e.getSceneX(), e.getSceneY(), view.getCenterX(), view.getCenterY()});
            });

            view.setOnMouseDragged(e -> {
                double[] data = (double[]) view.getUserData();
                double dx = e.getSceneX() - data[0];
                double dy = e.getSceneY() - data[1];
                double newX = data[2] + dx;
                double newY = data[3] + dy;

                view.setCenterX(newX);
                view.setCenterY(newY);
                this.x = newX;
                this.y = newY;
                updateTextPosition();

                for (GraphEdge edge : connectedEdges) {
                    edge.updatePosition();
                }
            });
        }

        void updateTextPosition() {
            if (text.getLayoutBounds() != null) {
                text.setX(x - text.getLayoutBounds().getWidth() / 2);
                text.setY(y + text.getLayoutBounds().getHeight() / 4);
            }
        }
    }

    // === EDGE CLASS ===
    private class GraphEdge {
        GraphNode from, to;
        Line line;
        Text label;

        GraphEdge(GraphNode from, GraphNode to, String labelText) {
            this.from = from;
            this.to = to;

            line = new Line(from.x, from.y, to.x, to.y);
            line.setStroke(Color.GRAY);
            line.setStrokeWidth(2);

            label = new Text(labelText);
            // label.setMouseTransparent(true);  ← Removed this to make label clickable

            label.setOnMouseClicked(e -> {
                if (selectedEdge != null) {
                    selectedEdge.line.setStroke(Color.GRAY); // un-highlight previous
                }
                selectedEdge = this;
                line.setStroke(Color.RED); // highlight selected
                textFieldEdge.setText(label.getText());
                statusLabel.setText("Selected edge: " + from.id + " → " + to.id);
            });

            label.layoutBoundsProperty().addListener((obs, oldVal, newVal) -> updatePosition());

            updatePosition();
        }

        void updatePosition() {
            line.setStartX(from.x);
            line.setStartY(from.y);
            line.setEndX(to.x);
            line.setEndY(to.y);

            double midX = (from.x + to.x) / 2;
            double midY = (from.y + to.y) / 2;
            label.setX(midX - label.getLayoutBounds().getWidth() / 2);
            label.setY(midY + label.getLayoutBounds().getHeight() / 4);
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
        statusLabel.setText("Ready");
    }

    private void handleAddNode() {
        String id = textFieldId.getText().trim();
        String type = textFieldType.getText().trim();
        String label = textFieldLabel.getText().trim();
        if (id.isEmpty()) id = "N" + nodeCounter;

        double paneWidth = graphCanvas.getWidth();
        double paneHeight = graphCanvas.getHeight();
        double radius = 25;
        double padding = 20;
        double space = 2 * radius + padding;

        int maxCols = (int) ((paneWidth - padding) / space);
        int maxRows = (int) ((paneHeight - padding) / space);
        if (maxCols < 1) maxCols = 1;
        if (maxRows < 1) maxRows = 1;

        Set<String> occupied = new HashSet<>();
        for (GraphNode node : nodes.values()) {
            int gridX = (int) (node.x / space);
            int gridY = (int) (node.y / space);
            occupied.add(gridX + "," + gridY);
        }

        int row = 0, col = 0;
        outer:
        for (int r = 0; r < maxRows; r++) {
            for (int c = 0; c < maxCols; c++) {
                String key = c + "," + r;
                if (!occupied.contains(key)) {
                    row = r;
                    col = c;
                    break outer;
                }
            }
        }

        double x = padding + col * space + radius;
        double y = padding + row * space + radius;

        nodeCounter++;

        GraphNode node = new GraphNode(id, type, label, x, y);
        nodes.put(id, node);
        graphCanvas.getChildren().addAll(node.view, node.text);

        node.view.setOnMouseClicked(event -> {
            if (selectedNode == null) {
                selectedNode = node;
                node.view.setStroke(Color.RED);
            } else if (secondSelectedNode == null && node != selectedNode) {
                secondSelectedNode = node;
                node.view.setStroke(Color.GREEN);
            } else {
                clearSelection();
            }
            populateNodeInfo(node);
        });

        statusLabel.setText("Node " + id + " added");
    }

    private void handleAddEdge() {
        if (selectedNode != null && secondSelectedNode != null) {
            String edgeLabel = textFieldEdge.getText().trim();
            GraphEdge edge = new GraphEdge(selectedNode, secondSelectedNode, edgeLabel);
            edges.add(edge);
            selectedNode.connectedEdges.add(edge);
            secondSelectedNode.connectedEdges.add(edge);

            graphCanvas.getChildren().add(0, edge.line);
            graphCanvas.getChildren().add(0, edge.label);

            statusLabel.setText("Edge created from " + selectedNode.id + " to " + secondSelectedNode.id);
            clearSelection();
        } else {
            statusLabel.setText("Select two nodes first.");
        }
    }

    private void handleUpdateNode() {
        if (selectedNode != null) {
            selectedNode.id = textFieldId.getText().trim();
            selectedNode.type = textFieldType.getText().trim();
            selectedNode.label = textFieldLabel.getText().trim();
            selectedNode.text.setText(selectedNode.label);
            selectedNode.updateTextPosition();
            statusLabel.setText("Updated node: " + selectedNode.id);
        } else {
            statusLabel.setText("No node selected to update.");
        }
    }

    private void handleUpdateEdge() {
        if (selectedEdge != null) {
            String newLabel = textFieldEdge.getText().trim();
            selectedEdge.label.setText(newLabel);
            statusLabel.setText("Edge label updated.");
        } else {
            statusLabel.setText("No edge selected.");
        }
    }

    private void handleDeleteNode() {
        if (selectedNode != null) {
            List<GraphEdge> toRemove = new ArrayList<>(selectedNode.connectedEdges);
            for (GraphEdge edge : toRemove) {
                removeEdge(edge);
            }
            graphCanvas.getChildren().removeAll(selectedNode.view, selectedNode.text);
            nodes.remove(selectedNode.id);
            statusLabel.setText("Deleted node: " + selectedNode.id);
            selectedNode = null;
            secondSelectedNode = null;
        } else {
            statusLabel.setText("No node selected to delete.");
        }
    }

    private void handleDeleteEdge() {
        if (selectedEdge != null) {
            removeEdge(selectedEdge);
            statusLabel.setText("Deleted selected edge.");
            selectedEdge = null;
        } else {
            statusLabel.setText("No edge selected to delete.");
        }
    }

    private void removeEdge(GraphEdge edge) {
        graphCanvas.getChildren().removeAll(edge.line, edge.label);
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
        if (selectedEdge != null) selectedEdge.line.setStroke(Color.GRAY);
        selectedNode = null;
        secondSelectedNode = null;
        selectedEdge = null;
    }
}
